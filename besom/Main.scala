import besom.*

@main def main: Unit = Pulumi.run {
  for 
    _ <- log.warn("Nothing's here yet, it's waiting for you to write some code!")
  yield Pulumi.exports()
}
