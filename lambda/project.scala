//> using toolkit "latest"

//> using lib "software.amazon.awssdk:dynamodb:2.20.149"
//> using lib "software.amazon.awssdk:s3:2.20.149"

//> using lib "commons-fileupload:commons-fileupload:1.5"

//> using packaging.graalvmArgs --no-fallback --static -H:+ReportExceptionStackTraces
//> using packaging.graalvmArgs --initialize-at-build-time=org.slf4j.LoggerFactory
//> using packaging.graalvmArgs --initialize-at-build-time=org.slf4j.simple.SimpleLogger
//> using packaging.graalvmArgs --initialize-at-build-time=org.slf4j.impl.StaticLoggerBinder
//> using packaging.graalvmArgs --initialize-at-run-time=io.netty.util.internal.logging.Log4JLogger
//> using packaging.graalvmArgs --initialize-at-run-time=io.netty.util.AbstractReferenceCounted
//> using packaging.graalvmArgs --initialize-at-run-time=io.netty.channel.epoll
//> using packaging.graalvmArgs --initialize-at-run-time=io.netty.handler.ssl
//> using packaging.graalvmArgs --initialize-at-run-time=io.netty.channel.unix
