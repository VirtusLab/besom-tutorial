import * as pulumi from "@pulumi/pulumi";
import * as aws from "@pulumi/aws";

export const bucketName = "pulumi-catpost-cat-pics";
const feedBucket = new aws.s3.Bucket(bucketName, {
  bucket: bucketName,
  // acl: "public-read",
  policy: JSON.stringify({
    "Version": "2012-10-17",
    "Statement": [
      {
        "Sid": "PublicReadGetObject",
        "Effect": "Allow",
        "Principal": "*",
        "Action": "s3:GetObject",
        "Resource": `arn:aws:s3:::${bucketName}/*`
      }
    ]
  })
});
/*

const tableName = "pulumi-catpost-table";
const catPostTable = new aws.dynamodb.Table("cat-post-table", {
  name: tableName,
  attributes: [
    { name: "PartitionKey", type: "S" },
    { name: "timestamp", type: "S" }
  ],
  hashKey: "PartitionKey",
  rangeKey: "timestamp",
  readCapacity: 5,
  writeCapacity: 5,
});

const entry1 = new aws.dynamodb.TableItem("entry1", {
  tableName: catPostTable.name,
  hashKey: catPostTable.hashKey,
  item: `{
    PartitionKey: { S: "ALL_ENTRIES" },
    timestamp: { S: "2023-09-16T12:00:00Z" },
    entryId: { S: "entry1" },
    userName: { S: "User1" },
    comment: { S: "Lovely cat!" },
    catPictureURL: { S: "https://cdn2.thecatapi.com/images/57r.jpg" }
  }`
});

const entry2 = new aws.dynamodb.TableItem("entry2", {
  tableName: catPostTable.name,
  hashKey: catPostTable.hashKey,
  item: `{
    PartitionKey: { S: "ALL_ENTRIES" },
    timestamp: { S: "2023-09-16T12:05:00Z" },
    entryId: { S: "entry2" },
    userName: { S: "User2" },
    comment: { S: "Such a cute kitty!" },
    catPictureURL: { S: "https://cdn2.thecatapi.com/images/buf.jpg" }
  }`
});

const entry3 = new aws.dynamodb.TableItem("entry3", {
  tableName: catPostTable.name,
  hashKey: catPostTable.hashKey,
  item: `{
    PartitionKey: { S: "ALL_ENTRIES" },
    timestamp: { S: "2023-09-16T12:10:00Z" },
    entryId: { S: "entry3" },
    userName: { S: "User3" },
    comment: { S: "Adorable!" },
    catPictureURL: { S: "https://cdn2.thecatapi.com/images/LzVDEMYIv.jpg" }
  }`
});

*/

const feedName = "pulumi-render-feed"
const stageName = "default"

const feedLambdaLogs = new aws.cloudwatch.LogGroup("/aws/lambda/" + feedName, {
    name: "/aws/lambda/" + feedName,
    retentionInDays: 3,
});

const feedLambda = new aws.lambda.Function(feedName, {
    name: feedName,
    role: "arn:aws:iam::294583657590:role/lambda-role", // default role
    runtime: "provided.al2", // Use the custom runtime
    code: new pulumi.asset.FileArchive("../pre-built/render-feed.zip"),
    handler: "whatever",
    environment: {
        variables: {
            "STAGE": "default",
            "BUCKET_NAME": bucketName,
        },
    },
});

const feedLambdaFunctionUrl = new aws.lambda.FunctionUrl("feedLambdaFunctionUrl", {
    authorizationType: "NONE",
    functionName: feedLambda.name
})

export const feedARN = feedLambda.arn

// Create a REST API Gateway
const api = new aws.apigateway.RestApi("api", {
    binaryMediaTypes: ["multipart/form-data"],
    endpointConfiguration: {
        types: "REGIONAL"
    }
});

const feedLambdaPermission = new aws.lambda.Permission("feedLambdaPermission", {
    action: "lambda:InvokeFunction",
    function: feedLambda.name,
    principal: "apigateway.amazonaws.com",
    sourceArn: pulumi.interpolate`${api.executionArn}/*`,
});

const feedMethod = new aws.apigateway.Method("feedMethod", {
    restApi: api,
    resourceId: api.rootResourceId, // use URL root path
    httpMethod: "GET",
    authorization: "NONE",
});

const feedIntegration = new aws.apigateway.Integration("feedIntegration", {
    restApi: api,
    resourceId: api.rootResourceId,
    httpMethod: feedMethod.httpMethod,
    integrationHttpMethod: feedMethod.httpMethod,
    type: "AWS_PROXY", // Lambda Proxy integration
    uri: feedLambda.invokeArn,
});

const apiDeployment = new aws.apigateway.Deployment("apiDeployment", {
    restApi: api,
    // workarounds for the terraform bugs
    triggers: {
        "resourceId": api.rootResourceId,
        "feedIntegrationId": feedIntegration.id,
        "feedMethodId": feedMethod.id,
    },
    stageDescription: pulumi.interpolate`${api.rootResourceId}${feedMethod.id}${feedIntegration.id}`
}, {
    deleteBeforeReplace: true
});

const apiStage = new aws.apigateway.Stage("apiStage", {
    restApi: api.id,
    deployment: apiDeployment.id,
    stageName: stageName,
});

const apiStageSettings = new aws.apigateway.MethodSettings("apiStageSettings", {
    restApi: api.id,
    stageName: apiStage.stageName,
    methodPath: "*/*",
    settings: {
        metricsEnabled: true,
        loggingLevel: "ERROR",
    },
});

export const endpointURL = apiStage.invokeUrl;