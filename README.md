# HotswapRef

[![Build & Release](https://github.com/janstenpickle/hotswap-ref/workflows/Build%20&%20Release/badge.svg)](https://github.com/janstenpickle/trace4cats/actions?query=workflow%3A%22Build+%26+Release%22)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.janstenpickle/hotswap-ref_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.janstenpickle/trace4cats-core_2.13)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

Utility built on top of [cats-effect](https://typelevel.org/cats-effect/)
[`Hotswap`](https://typelevel.org/cats-effect/docs/std/hotswap). Decouples the operations of swapping and accessing a
resource, allowing them to be performed within different fibers.

## Quickstart


Add the following dependencies to your `build.sbt`:

```scala
"io.janstenpickle" %% "hotswap-ref" % "0.1.0"
```

## Usage

See [HotswapRef](modules/core/src/main/scala/io/janstenpickle/hotswapref/HotswapRef.scala),
[HotswapRefConstructor](modules/core/src/main/scala/io/janstenpickle/hotswapref/HotswapRefConstructor.scala) and
[ConditionalHotswapRefConstructor](modules/core/src/main/scala/io/janstenpickle/hotswapref/ConditionalHotswapRefConstructor.scala)
for Scaladoc notes.

## Contributing

This project supports the [Scala Code of Conduct](https://typelevel.org/code-of-conduct.html) and aims that its channels
(mailing list, Discord, github, etc.) to be welcoming environments for everyone.
