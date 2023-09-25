import * as pulumi from "@pulumi/pulumi";
import * as aws from "@pulumi/aws";

// Public access settings for the bucket
const feedBucket = new aws.s3.Bucket("pulumi-catpost-cat-pics", {
    // the bucket name will be randomly generated unless explicitly set
    forceDestroy: true, // change to true when destroying completely
});

export const bucketName = feedBucket.bucket

bucketName.apply(bucketName => {
    const feedBucketPublicAccessBlock = new aws.s3.BucketPublicAccessBlock(`${bucketName}-publicaccessblock`, {
        bucket: feedBucket.id,
        blockPublicAcls: false,      // Do not block public ACLs for this bucket
        blockPublicPolicy: false,    // Do not block public bucket policies for this bucket
        ignorePublicAcls: false,     // Do not ignore public ACLs for this bucket
        restrictPublicBuckets: false // Do not restrict public bucket policies for this bucket
    });

    // need to create a bucket policy separately to allow public access to the bucket due to AWS race condition
    new aws.s3.BucketPolicy(`${bucketName}-access-policy`, {
        bucket: feedBucket.id,
        policy: JSON.stringify({
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Sid": "PublicReadGetObject",
                    "Effect": "Allow",
                    "Principal": "*",
                    "Action": [
                        "s3:GetObject"
                    ],
                    "Resource": [
                        `arn:aws:s3:::${bucketName}/*`
                    ]
                }
            ]
        })
    }, {
        dependsOn: feedBucketPublicAccessBlock
    });
});

const tableName = "pulumi-catpost-table";

const catPostTable = new aws.dynamodb.Table(tableName, {
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

const stageName = "default"
const feedName = "pulumi-render-feed"
const addName = "pulumi-add-post"

const feedLambdaLogs = new aws.cloudwatch.LogGroup("/aws/lambda/" + feedName, {
    name: "/aws/lambda/" + feedName,
    retentionInDays: 3,
});

const addLambdaLogs = new aws.cloudwatch.LogGroup("/aws/lambda/" + addName, {
    name: "/aws/lambda/" + addName,
    retentionInDays: 3,
});

const feedLambda = bucketName.apply(bucketName => {
    return new aws.lambda.Function(feedName, {
        name: feedName,
        role: "arn:aws:iam::294583657590:role/lambda-role", // default role
        runtime: "provided.al2", // Use the custom runtime
        code: new pulumi.asset.FileArchive("../pre-built/render-feed.zip"),
        handler: "whatever",
        environment: {
            variables: {
                "STAGE": stageName,
                "BUCKET_NAME": bucketName,
                "DYNAMO_TABLE": tableName,
            },
        },
    })
});

const addLambda = bucketName.apply(bucketName => {
    return new aws.lambda.Function(addName, {
        name: addName,
        role: "arn:aws:iam::294583657590:role/lambda-role", // default role
        runtime: "provided.al2", // Use the custom runtime
        code: new pulumi.asset.FileArchive("../pre-built/post-cat-entry.zip"),
        handler: "whatever",
        environment: {
            variables: {
                "STAGE": stageName,
                "BUCKET_NAME": bucketName,
                "DYNAMO_TABLE": tableName,
            },
        },
    })
});

const feedLambdaFunctionUrl = new aws.lambda.FunctionUrl("feedLambdaFunctionUrl", {
    authorizationType: "NONE",
    functionName: feedLambda.name
})

const addLambdaFunctionUrl = new aws.lambda.FunctionUrl("addLambdaFunctionUrl", {
    authorizationType: "NONE",
    functionName: addLambda.name
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

const addLambdaPermission = new aws.lambda.Permission("addLambdaPermission", {
    action: "lambda:InvokeFunction",
    function: addLambda.name,
    principal: "apigateway.amazonaws.com",
    sourceArn: pulumi.interpolate`${api.executionArn}/*`,
});

const feedMethod = new aws.apigateway.Method("feedMethod", {
    restApi: api,
    resourceId: api.rootResourceId, // use URL root path
    httpMethod: "GET",
    authorization: "NONE",
});

const addResource = new aws.apigateway.Resource("addResource", {
    restApi: api,
    pathPart: "post",
    parentId: api.rootResourceId,
})

const addMethod = new aws.apigateway.Method("addMethod", {
    restApi: api,
    resourceId: addResource.id, // use URL root path
    httpMethod: "POST",
    authorization: "NONE",
});

const feedIntegration = new aws.apigateway.Integration("feedIntegration", {
    restApi: api,
    resourceId: api.rootResourceId,
    httpMethod: feedMethod.httpMethod,
    // must be POST, this is not a mistake: https://repost.aws/knowledge-center/api-gateway-lambda-template-invoke-error
    integrationHttpMethod: "POST",
    type: "AWS_PROXY", // Lambda Proxy integration
    uri: feedLambda.invokeArn,
});

const addIntegration = new aws.apigateway.Integration("addIntegration", {
    restApi: api,
    resourceId: addResource.id,
    httpMethod: addMethod.httpMethod,
    // must be POST, this is not a mistake: https://repost.aws/knowledge-center/api-gateway-lambda-template-invoke-error
    integrationHttpMethod: "POST",
    type: "AWS_PROXY", // Lambda Proxy integration
    uri: addLambda.invokeArn,
});

const apiDeployment = new aws.apigateway.Deployment("apiDeployment", {
    restApi: api,
    // workarounds for the underlying provider bugs
    triggers: {
        "resourceId": api.rootResourceId,
        "feedMethodId": feedMethod.id,
        "feedIntegrationId": feedIntegration.id,
        "addResourceId": addResource.id,
        "addMethodId": addMethod.id,
        "addIntegrationId": addIntegration.id,
    },
}, {
    dependsOn: [feedLambda, addLambda],
    deleteBeforeReplace: false,
});

const apiStage = new aws.apigateway.Stage("apiStage", {
    restApi: api.id,
    deployment: apiDeployment.id,
    stageName: stageName,
}, {
    deleteBeforeReplace: true,
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