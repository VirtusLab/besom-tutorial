package besom.examples.lambda

import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime

object crypto:
  def hmacSha256(key: String, data: String): String =
    val sks = SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(sks)
    val hmac = mac.doFinal(data.getBytes("UTF-8"))
    hmac.map("%02x".format(_)).mkString

  def sha256(data: String): String =
    val md = java.security.MessageDigest.getInstance("SHA-256")
    md.digest(data.getBytes("UTF-8")).map("%02x".format(_)).mkString

  def signRequest(
    accessKeyId: String,
    secretAccessKey: String,
    region: String,
    serviceName: String,
    httpMethod: String,
    canonicalUri: String,
    canonicalQueryString: String,
    canonicalHeaders: Map[String, String],
    payload: String
  ): Map[String, String] =
    val algorithm   = "AWS4-HMAC-SHA256"
    val currentDate = DateTimeFormatter.ofPattern("yyyyMMdd").format(ZonedDateTime.now())
    val dateTime    = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").format(ZonedDateTime.now())

    val sortedHeaders = canonicalHeaders.toList
      .sortBy(_._1)
      .map { case (k, v) =>
        s"$k:${v.trim}\n"
      }
      .mkString

    val signedHeaders = canonicalHeaders.keys.toList.sorted.mkString(";")

    val payloadHash = sha256(payload)

    val canonicalRequest =
      s"$httpMethod\n$canonicalUri\n$canonicalQueryString\n$sortedHeaders\n$signedHeaders\n$payloadHash"

    val credentialScope = s"$currentDate/$region/$serviceName/aws4_request"
    val stringToSign    = s"$algorithm\n$dateTime\n$credentialScope\n${sha256(canonicalRequest)}"

    def deriveSigningKey(secret: String, date: String, region: String, service: String): String =
      val kDate    = hmacSha256(s"AWS4$secret", date)
      val kRegion  = hmacSha256(kDate, region)
      val kService = hmacSha256(kRegion, service)
      hmacSha256(kService, "aws4_request")

    val signingKey = deriveSigningKey(secretAccessKey, currentDate, region, serviceName)
    val signature  = hmacSha256(signingKey, stringToSign)

    val authorizationHeader =
      s"$algorithm Credential=$accessKeyId/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

    Map(
      "Authorization" -> authorizationHeader,
      "X-Amz-Date" -> dateTime
    )
