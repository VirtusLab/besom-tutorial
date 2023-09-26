import besom.*
import besom.api.aws.*
import besom.api.aws.apigateway.inputs.*
import besom.api.aws.lambda.inputs.*
import besom.api.aws.dynamodb.inputs.*
import besom.types.Archive.FileArchive
import spray.json.*

@main def main: Unit = Pulumi.run {

  val feedBucket = s3.Bucket(
    "pulumi-catpost-cat-pics",
    s3.BucketArgs(
      forceDestroy = true
    )
  )

  val bucketName = feedBucket.bucket

  val feedBucketPublicAccessBlock = bucketName.flatMap { name =>
    s3.BucketPublicAccessBlock(
      s"${name}-publicaccessblock",
      s3.BucketPublicAccessBlockArgs(
        bucket = feedBucket.id,
        blockPublicPolicy = false // Do not block public bucket policies for this bucket
      )
    )
  }

  val feedBucketPolicy = bucketName.flatMap(name =>
    s3.BucketPolicy(
      s"${name}-access-policy",
      s3.BucketPolicyArgs(
        bucket = feedBucket.id,
        policy = JsObject(
          "Version" -> JsString("2012-10-17"),
          "Statement" -> JsArray(
            JsObject(
              "Sid" -> JsString("PublicReadGetObject"),
              "Effect" -> JsString("Allow"),
              "Principal" -> JsObject(
                "AWS" -> JsString("*")
              ),
              "Action" -> JsArray(JsString("s3:GetObject")),
              "Resource" -> JsArray(JsString(s"arn:aws:s3:::${name}/*"))
            )
          )
        ).prettyPrint
      ),
      CustomResourceOptions(
        dependsOn = feedBucketPublicAccessBlock.map(List(_))
      )
    )
  )

  val catPostTable = dynamodb.Table(
    "pulumi-catpost-table",
    dynamodb.TableArgs(
      attributes = List(
        TableAttributeArgs(
          name = "PartitionKey",
          `type` = "S"
        ),
        TableAttributeArgs(
          name = "timestamp",
          `type` = "S"
        )
      ),
      hashKey = "PartitionKey",
      rangeKey = "timestamp",
      readCapacity = 5,
      writeCapacity = 5
    )
  )

  val stageName: NonEmptyString = "default"

  val lambdaRole = iam.Role("lambda-role", iam.RoleArgs(
    assumeRolePolicy = JsObject(
      "Version" -> JsString("2012-10-17"),
      "Statement" -> JsArray(
        JsObject(
          "Action" -> JsString("sts:AssumeRole"),
          "Effect" -> JsString("Allow"),
          "Principal" -> JsObject(
            "Service" -> JsString("lambda.amazonaws.com")
          )
        )
      )
    ).prettyPrint,
    forceDetachPolicies = true,
    managedPolicyArns = List(
      "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole",
      "arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess",
      "arn:aws:iam::aws:policy/AmazonS3FullAccess",
    )
  ))

  val feedLambda = lambda.Function(
    "pulumi-render-feed",
    lambda.FunctionArgs(
      role = lambdaRole.arn,
      runtime = "provided.al2", // Use the custom runtime
      code = FileArchive("../pre-built/render-feed.zip"),
      handler = "whatever", // we don't use the handler, but it's required
      environment = FunctionEnvironmentArgs(
        variables = Map(
          "STAGE" -> stageName,
          "BUCKET_NAME" -> bucketName,
          "DYNAMO_TABLE" -> catPostTable.name
        )
      )
    )
  )

  val addLambda = lambda.Function(
    "pulumi-add-post",
    lambda.FunctionArgs(
      role = lambdaRole.arn,
      runtime = "provided.al2", // Use the custom runtime
      code = FileArchive("../pre-built/post-cat-entry.zip"),
      handler = "whatever", // we don't use the handler, but it's required
      environment = FunctionEnvironmentArgs(
        variables = Map(
          "STAGE" -> stageName,
          "BUCKET_NAME" -> bucketName,
          "DYNAMO_TABLE" -> catPostTable.name
        )
      )
    )
  )

  val feedLambdaLogs = feedLambda.name.flatMap { feedName => 
    val feedLambdaLogsName: NonEmptyString = s"/aws/lambda/$feedName"

    cloudwatch.LogGroup(
      feedLambdaLogsName,
      cloudwatch.LogGroupArgs(
        name = feedLambdaLogsName,
        retentionInDays = 3
      )
    )
  }

  val addLambdaLogs = addLambda.name.flatMap { addName => 
    val addLambdaLogsName: NonEmptyString = s"/aws/lambda/$addName"

    cloudwatch.LogGroup(
      addLambdaLogsName,
      cloudwatch.LogGroupArgs(
        name = addLambdaLogsName,
        retentionInDays = 3
      )
    )
  }

  val api = apigateway.RestApi(
    "api",
    apigateway.RestApiArgs(
      binaryMediaTypes = List("multipart/form-data"),
      endpointConfiguration = RestApiEndpointConfigurationArgs(types = "REGIONAL")
    )
  )
  val feedLambdaPermission = lambda.Permission(
    "feedLambdaPermission",
    lambda.PermissionArgs(
      action = "lambda:InvokeFunction",
      function = feedLambda.name,
      principal = "apigateway.amazonaws.com",
      sourceArn = p"${api.executionArn}/*"
    )
  )

  val addLambdaPermission = lambda.Permission(
    "addLambdaPermission",
    lambda.PermissionArgs(
      action = "lambda:InvokeFunction",
      function = addLambda.name,
      principal = "apigateway.amazonaws.com",
      sourceArn = p"${api.executionArn}/*"
    )
  )

  val feedMethod = apigateway.Method(
    "feedMethod",
    apigateway.MethodArgs(
      restApi = api.id,
      resourceId = api.rootResourceId,
      httpMethod = "GET",
      authorization = "NONE"
    )
  )

  val addResource = apigateway.Resource(
    "addResource",
    apigateway.ResourceArgs(
      restApi = api.id,
      pathPart = "post",
      parentId = api.rootResourceId
    )
  )

  val addMethod = apigateway.Method(
    "addMethod",
    apigateway.MethodArgs(
      restApi = api.id,
      resourceId = addResource.id,
      httpMethod = "POST",
      authorization = "NONE"
    )
  )

  val feedIntegration = apigateway.Integration(
    "feedIntegration",
    apigateway.IntegrationArgs(
      restApi = api.id,
      resourceId = api.rootResourceId, // use / path
      httpMethod = feedMethod.httpMethod,
      // must be POST, this is not a mistake: https://repost.aws/knowledge-center/api-gateway-lambda-template-invoke-error
      integrationHttpMethod = "POST",
      `type` = "AWS_PROXY", // Lambda Proxy integration
      uri = feedLambda.invokeArn
    )
  )

  val addIntegration = apigateway.Integration(
    "addIntegration",
    apigateway.IntegrationArgs(
      restApi = api.id,
      resourceId = addResource.id, // use /post path
      httpMethod = addMethod.httpMethod,
      // must be POST, this is not a mistake: https://repost.aws/knowledge-center/api-gateway-lambda-template-invoke-error
      integrationHttpMethod = "POST",
      `type` = "AWS_PROXY", // Lambda Proxy integration
      uri = addLambda.invokeArn
    )
  )

  val apiDeployment = apigateway.Deployment(
    "apiDeployment",
    apigateway.DeploymentArgs(
      restApi = api.id,
      // workarounds for the underlying provider bugs
      triggers = Map(
        "resourceId" -> api.rootResourceId,
        "feedMethodId" -> feedMethod.id,
        "feedIntegrationId" -> feedIntegration.id,
        "addResourceId" -> addResource.id,
        "addMethodId" -> addMethod.id,
        "addIntegrationId" -> addIntegration.id,
      )
    ),
    CustomResourceOptions(
      dependsOn = Output.sequence(List(feedLambda, addLambda)),
      deleteBeforeReplace = false
    )
  )

  val apiStage = apigateway.Stage(
    "apiStage",
    apigateway.StageArgs(
      restApi = api.id,
      deployment = apiDeployment.id,
      stageName = stageName
    ),
    CustomResourceOptions(
      deleteBeforeReplace = true
    )
  )

  val apiStageSettings = apigateway.MethodSettings(
    "apiStageSettings",
    apigateway.MethodSettingsArgs(
      restApi = api.id,
      stageName = apiStage.stageName,
      methodPath = "*/*",
      settings = MethodSettingsSettingsArgs(
        metricsEnabled = true,
        loggingLevel = "ERROR"
      )
    )
  )

  for
    bucket   <- feedBucket
    _        <- feedBucketPolicy
    _        <- feedBucketPublicAccessBlock 
    _        <- catPostTable
    _        <- feedLambdaLogs
    _        <- addLambdaLogs
    _        <- feedLambda
    _        <- addLambda
    _        <- api
    _        <- feedLambdaPermission
    _        <- addLambdaPermission
    _        <- feedMethod
    _        <- addResource
    _        <- addMethod
    _        <- feedIntegration
    _        <- addIntegration
    _        <- apiDeployment
    apiStage <- apiStage
    _        <- apiStageSettings
  yield Pulumi.exports(
    feedBucket = bucket.bucket,
    endpointURL = apiStage.invokeUrl
  )
}
