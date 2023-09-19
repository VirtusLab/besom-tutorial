import * as pulumi from "@pulumi/pulumi";
import * as aws from "@pulumi/aws";


const bucketName = "pulumi-catpost-cat-pics";
const catPicsBucket = new aws.s3.Bucket("pulumi-catpost-cat-pics-bucket", {
  bucket: bucketName,
  acl: "public-read",
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

