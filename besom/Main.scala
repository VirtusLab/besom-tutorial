import besom.*
import besom.Input
import besom.api.aws.*
import besom.api.aws.apigateway.inputs.*
import besom.api.aws.lambda.inputs.*
import besom.api.aws.dynamodb.inputs.*
import besom.types.Archive.FileArchive

@main def main(): Unit = Pulumi.run {
  val bucketName: NonEmptyString = "pulumi-catpost-cat-pics"
  val feedBucket = s3.Bucket(
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
                  |      "Resource": "arn:aws:s3:::${bucketName}/*"
                  |    }
                  |  ]
                  |}""".stripMargin
    )
  )

  val bucketPublicAccessBlock = s3.BucketPublicAccessBlock(
    s"${bucketName}-publicaccessblock",
    s3.BucketPublicAccessBlockArgs(
      bucket = feedBucket.id.map(_.asString), // FIXME this is a hack
      blockPublicAcls = false, // Do not block public ACLs for this bucket
      blockPublicPolicy = false, // Do not block public bucket policies for this bucket
      ignorePublicAcls = false, // Do not ignore public ACLs for this bucket
      restrictPublicBuckets = false // Do not restrict public bucket policies for this bucket
    )
  )

  val tableName: NonEmptyString = "pulumi-catpost-table";
  val catPostTable = dynamodb.Table(
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
  val feedLambdaLogs = cloudwatch.LogGroup(
    feedLambdaLogsName,
    cloudwatch.LogGroupArgs(
      name = feedLambdaLogsName,
      retentionInDays = 3
    )
  )

  val addLambdaLogsName: NonEmptyString = s"/aws/lambda/$addName"
  val addLambdaLogs = cloudwatch.LogGroup(
    addLambdaLogsName,
    cloudwatch.LogGroupArgs(
      name = addLambdaLogsName,
      retentionInDays = 3
    )
  )

  val feedLambda = lambda.Function(
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

  val addLambda = lambda.Function(
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

  val feedLambdaFunctionUrl = lambda.FunctionUrl(
    "feedLambdaFunctionUrl",
    lambda.FunctionUrlArgs(
      authorizationType = "NONE",
      functionName = feedLambda.name
    )
  )

  val addLambdaFunctionUrl = lambda.FunctionUrl(
    "addLambdaFunctionUrl",
    lambda.FunctionUrlArgs(
      authorizationType = "NONE",
      functionName = addLambda.name
    )
  )

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
      restApi = api.id.map(_.asString), // FIXME: this is a hack
      resourceId = api.rootResourceId,
      httpMethod = "GET",
      authorization = "NONE"
    )
  )

  val addResource = apigateway.Resource(
    "addResource",
    apigateway.ResourceArgs(
      restApi = api.id.map(_.asString), // FIXME: this is a hack
      pathPart = "post",
      parentId = api.rootResourceId
    )
  )

  val addMethod = apigateway.Method(
    "addMethod",
    apigateway.MethodArgs(
      restApi = api.id.map(_.asString), // FIXME: this is a hack
      resourceId = addResource.id.map(_.asString), // FIXME: this is a hack
      httpMethod = "POST",
      authorization = "NONE",
    )
  )

  val feedIntegration = apigateway.Integration(
    "feedIntegration",
    apigateway.IntegrationArgs(
      restApi = api.id.map(_.asString), // FIXME: this is a hack
      resourceId = api.rootResourceId,
      httpMethod = feedMethod.httpMethod,
      integrationHttpMethod = "POST",
      `type` = "AWS_PROXY",
      uri = feedLambda.invokeArn
    )
  )

  val addIntegration = apigateway.Integration(
    "addIntegration",
    apigateway.IntegrationArgs(
      restApi = api.id.map(_.asString), // FIXME this is a hack
      resourceId = addResource.id.map(_.asString), // FIXME this is a hack
      httpMethod = addMethod.httpMethod,
      integrationHttpMethod = "POST",
      `type` = "AWS_PROXY",
      uri = addLambda.invokeArn
    )
  )

  val apiDeployment = apigateway.Deployment(
    "apiDeployment",
    apigateway.DeploymentArgs(
    restApi = api.id.map(_.asString), // FIXME this is a hack
      triggers = Map(
        "resourceId" -> api.rootResourceId,
        "feedMethodId" -> feedMethod.id.map(_.asString), // FIXME this is a hack
        "feedIntegrationId" -> feedIntegration.id.map(_.asString), // FIXME this is a hack
        "addResourceId" -> addResource.id.map(_.asString), // FIXME this is a hack
        "addMethodId" -> addMethod.id.map(_.asString), // FIXME this is a hack
        "addIntegrationId" -> addIntegration.id.map(_.asString), // FIXME this is a hack
      )
    ),
    CustomResourceOptions(
      dependsOn = Output.sequence(List(feedLambda, addLambda)), // FIXME: this is a hack
      deleteBeforeReplace = false
    )
  )

  val apiStage = apigateway.Stage(
    "apiStage",
    apigateway.StageArgs(
      restApi = api.id.map(_.asString), // FIXME this is a hack
      deployment = apiDeployment.id.map(_.asString), // FIXME this is a hack
      stageName = stageName
    ),
    CustomResourceOptions(
      deleteBeforeReplace = true
    )
  )

  val apiStageSettings = apigateway.MethodSettings(
    "apiStageSettings",
    apigateway.MethodSettingsArgs(
      restApi = api.id.map(_.asString), // FIXME this is a hack
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
