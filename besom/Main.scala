import besom.*
import besom.Input
import besom.api.aws.*
import besom.api.aws.apigateway.inputs.*
import besom.api.aws.lambda.inputs.*
import besom.api.aws.dynamodb.inputs.*
import besom.types.Archive.FileArchive

@main def main(): Unit = Pulumi.run {
  val bucketName: NonEmptyString = "pulumi-catpost-cat-pics"
  val feedBucket = s3.bucket(
    bucketName,
    s3.BucketArgs(
      bucket = bucketName,
      forceDestroy = false, // change to true when destroying completely
      // todo: spray the string
      policy = s"""{
                  |  "Version": "2012-10-17",
                  |  "Statement": [
                  |    {
                  |      "Sid": "PublicReadGetObject",
                  |      "Effect": "Allow",
                  |      "Principal": "*",
                  |      "Action": "s3:GetObject",
                  |      "Resource": `arn:aws:s3:::${bucketName}/*`
                  |    }
                  |  ]
                  |}""".stripMargin
    )
  )

  val bucketPublicAccessBlock = s3.bucketPublicAccessBlock(
    s"${bucketName}-publicaccessblock",
    s3.BucketPublicAccessBlockArgs(
      bucket = feedBucket.id,
      blockPublicAcls = false, // Do not block public ACLs for this bucket
      blockPublicPolicy = false, // Do not block public bucket policies for this bucket
      ignorePublicAcls = false, // Do not ignore public ACLs for this bucket
      restrictPublicBuckets = false // Do not restrict public bucket policies for this bucket
    )
  )

  val tableName: NonEmptyString = "pulumi-catpost-table";
  val catPostTable = dynamodb.table(
    tableName,
    dynamodb.TableArgs(
      name = tableName,
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
  val feedName: NonEmptyString  = "pulumi-render-feed"
  val addName: NonEmptyString   = "pulumi-add-post"

  val feedLambdaLogsName: NonEmptyString = s"/aws/lambda/$feedName"
  val feedLambdaLogs = cloudwatch.logGroup(
    feedLambdaLogsName,
    cloudwatch.LogGroupArgs(
      name = feedLambdaLogsName,
      retentionInDays = 3
    )
  )

  val addLambdaLogsName: NonEmptyString = s"/aws/lambda/$addName"
  val addLambdaLogs = cloudwatch.logGroup(
    addLambdaLogsName,
    cloudwatch.LogGroupArgs(
      name = addLambdaLogsName,
      retentionInDays = 3
    )
  )

  val feedLambda = lambda.function(
    feedName,
    lambda.FunctionArgs(
      name = feedName,
      role = "arn:aws:iam::294583657590:role/lambda-role",
      runtime = "provided.al2",
      code = FileArchive("../pre-built/render-feed.zip"),
      handler = "whatever",
      environment = FunctionEnvironmentArgs(
        variables = Map(
          "STAGE" -> stageName,
          "BUCKET_NAME" -> bucketName,
          "DYNAMO_TABLE" -> tableName
        )
      )
    )
  )

  val addLambda = lambda.function(
    addName,
    lambda.FunctionArgs(
      name = addName,
      role = "arn:aws:iam::294583657590:role/lambda-role",
      runtime = "provided.al2",
      code = FileArchive("../pre-built/post-cat-entry.zip"),
      handler = "whatever",
      environment = FunctionEnvironmentArgs(
        variables = Map(
          "STAGE" -> stageName,
          "BUCKET_NAME" -> bucketName,
          "DYNAMO_TABLE" -> tableName
        )
      )
    )
  )

  val feedLambdaFunctionUrl = lambda.functionUrl(
    "feedLambdaFunctionUrl",
    lambda.FunctionUrlArgs(
      authorizationType = "NONE",
      functionName = feedLambda.name
    )
  )

  val addLambdaFunctionUrl = lambda.functionUrl(
    "addLambdaFunctionUrl",
    lambda.FunctionUrlArgs(
      authorizationType = "NONE",
      functionName = addLambda.name
    )
  )

  val api = apigateway.restApi(
    "api",
    apigateway.RestApiArgs(
      binaryMediaTypes = List("multipart/form-data"),
      endpointConfiguration = RestApiEndpointConfigurationArgs(types = "REGIONAL")
    )
  )
  val feedLambdaPermission = lambda.permission(
    "feedLambdaPermission",
    lambda.PermissionArgs(
      action = "lambda:InvokeFunction",
      function = feedLambda.name,
      principal = "apigateway.amazonaws.com",
      sourceArn = s"${api.executionArn}/*"
    )
  )

  val addLambdaPermission = lambda.permission(
    "addLambdaPermission",
    lambda.PermissionArgs(
      action = "lambda:InvokeFunction",
      function = addLambda.name,
      principal = "apigateway.amazonaws.com",
      sourceArn = s"${api.executionArn}/*"
    )
  )

  val feedMethod = apigateway.method(
    "feedMethod",
    apigateway.MethodArgs(
      restApi = api.id.asInstanceOf[String], // FIXME: this is a hack
      resourceId = api.rootResourceId,
      httpMethod = "GET",
      authorization = "NONE"
    )
  )

  val addResource = apigateway.resource(
    "addResource",
    apigateway.ResourceArgs(
      restApi = api.id.asInstanceOf[String], // FIXME: this is a hack,
      pathPart = "post",
      parentId = api.rootResourceId
    )
  )

  val addMethod = apigateway.method(
    "addMethod",
    apigateway.MethodArgs(
      restApi = api.id.asInstanceOf[String], // FIXME: this is a hack,
      resourceId = addResource.id.asInstanceOf[String], // FIXME: this is a hack
      httpMethod = "POST",
      authorization = "NONE",
    )
  )

  val feedIntegration = apigateway.integration(
    "feedIntegration",
    apigateway.IntegrationArgs(
      restApi = api.id.asInstanceOf[String], // FIXME: this is a hack,
      resourceId = api.rootResourceId.asInstanceOf[String], // FIXME: this is a hack
      httpMethod = feedMethod.httpMethod,
      integrationHttpMethod = "POST",
      `type` = "AWS_PROXY",
      uri = feedLambda.invokeArn
    )
  )

  val addIntegration = apigateway.integration(
    "addIntegration",
    apigateway.IntegrationArgs(
      restApi = api.id.asInstanceOf[String], // FIXME: this is a hack
      resourceId = addResource.id.asInstanceOf[String], // FIXME: this is a hack
      httpMethod = addMethod.httpMethod,
      integrationHttpMethod = "POST",
      `type` = "AWS_PROXY",
      uri = addLambda.invokeArn
    )
  )

  val apiDeployment = apigateway.deployment(
    "apiDeployment",
    apigateway.DeploymentArgs(
    restApi = api.id.asInstanceOf[String], // FIXME: this is a hack
      triggers = Map(
        "resourceId" -> api.rootResourceId.asInstanceOf[String], // FIXME: this is a hack
        "feedMethodId" -> feedMethod.id.asInstanceOf[String], // FIXME: this is a hack
        "feedIntegrationId" -> feedIntegration.id.asInstanceOf[String], // FIXME: this is a hack
        "addResourceId" -> addResource.id.asInstanceOf[String], // FIXME: this is a hack
        "addMethodId" -> addMethod.id.asInstanceOf[String], // FIXME: this is a hack
        "addIntegrationId" -> addIntegration.id.asInstanceOf[String] // FIXME: this is a hack
      )
    ),
    CustomResourceOptions(
      dependsOn = Output.sequence(List(feedLambda, addLambda)), // FIXME: this is a hack
      deleteBeforeReplace = false
    )
  )

  val apiStage = apigateway.stage(
    "apiStage",
    apigateway.StageArgs(
      restApi = api.id.asInstanceOf[String], // FIXME: this is a hack
      deployment = apiDeployment.id.asInstanceOf[String], // FIXME: this is a hack
      stageName = stageName
    ),
    CustomResourceOptions(
      deleteBeforeReplace = true
    )
  )

  val apiStageSettings = apigateway.methodSettings(
    "apiStageSettings",
    apigateway.MethodSettingsArgs(
      restApi = api.id.asInstanceOf[String], // FIXME: this is a hack
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
    _        <- bucketPublicAccessBlock
    _        <- catPostTable
    _        <- feedLambdaLogs
    _        <- addLambdaLogs
    _        <- feedLambda
    _        <- addLambda
    _        <- feedLambdaFunctionUrl
    _        <- addLambdaFunctionUrl
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
    endpointURL = apiStage.invokeUrl,
  )
}
