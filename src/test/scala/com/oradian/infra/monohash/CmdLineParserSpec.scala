package com.oradian.infra.monohash

import java.security.Security
import java.util.UUID

import org.specs2.execute.Result
import org.specs2.matcher.MatchResult

class CmdLineParserSpec extends Specification {
  sequential

  private[this] def withParser(args: String*)(f: CmdLineParser => MatchResult[_]*): Result = {
    val logger = new LoggingLogger
    val parser = new CmdLineParser(args.toArray, _ => logger)
    Result.foreach(f) { expectation =>
      expectation(parser)
    }
  }

  "Empty argument handling" >> {
    "No args provided" >> {
      withParser()() must throwAn[ExitException]("You did not specify the \\[hash plan file\\]")
    }
    "Hash plan file is empty" >> {
      withParser("")() must throwAn[ExitException]("Provided \\[hash plan file\\] was an empty string")
    }
    "Export file is empty" >> {
      withParser("x", "")() must throwAn[ExitException]("Provided \\[export file\\] was an empty string")
    }
  }

  private[this] val fakePlan = UUID.randomUUID().toString
  private[this] val fakeExport = UUID.randomUUID().toString

  "Simple hash plan" >> {
    withParser(fakePlan)(
      _.hashPlanPath ==== fakePlan,
      _.exportPath ==== null,
    )

    withParser(fakePlan, fakeExport)(
      _.hashPlanPath ==== fakePlan,
      _.exportPath ==== fakeExport,
    )
  }

  "Default options" >> {
    withParser(fakePlan)(
      _.logLevel ==== Logger.Level.INFO,
      _.algorithm.name ==== "SHA-1",
      _.concurrency must be > 0,
      _.verification ==== Verification.OFF,
      _.hashPlanPath ==== fakePlan,
      _.exportPath ==== null,
    )
  }

  "Option parsing" >> {
    "Log level parsing" >> {
      withParser("-l")() must throwAn[ExitException]("Missing value for log level, last argument was an alone '-l'")
      withParser("-l", "")() must throwAn[ExitException]("Empty value provided for log level")
      withParser("-l", "--")() must throwAn[ExitException]("Missing value for log level, next argument was the stop flag '--'")
      withParser("-l", fakePlan)() must throwAn[ExitException](s"Unknown log level: '$fakePlan', supported log levels are: off, error, warn, info, debug, trace")
      withParser("-l", "xxx", fakePlan)() must throwAn[ExitException]("Unknown log level: 'xxx', supported log levels are: off, error, warn, info, debug, trace")
      withParser("-l", "off", fakePlan)(
        _.logLevel ==== Logger.Level.OFF,
        _.exportPath ==== null,
      )
      withParser("-l", "OfF", "--", fakePlan)(
        _.logLevel ==== Logger.Level.OFF,
        _.exportPath ==== null,
      )
      withParser("-l", "off", "-l", "error", fakePlan)(
        _.logLevel ==== Logger.Level.ERROR,
        _.exportPath ==== null,
      )
    }

    "Algorithm parsing" >> {
      withParser("-a")() must throwAn[ExitException]("Missing value for algorithm, last argument was an alone '-a'")
      withParser("-a", "")() must throwAn[ExitException]("Empty value provided for algorithm")
      withParser("-a", "--")() must throwAn[ExitException]("Missing value for algorithm, next argument was the stop flag '--'")
      withParser("-a", fakePlan)() must throwAn[ExitException](s"Algorithm '$fakePlan' is not supported. Supported algorithms:")
      withParser("-axxx", fakePlan)() must throwAn[ExitException]("Algorithm 'xxx' is not supported. Supported algorithms:")
      withParser("-a", "gIt", fakePlan)(
        _.algorithm.name ==== "Git",
        _.exportPath ==== null,
      )
      withParser("-aSHA-256", "--", fakePlan)(
        _.algorithm.name ==== "SHA-256",
        _.exportPath ==== null,
      )
      withParser("-a", "SHA-256", "-a", "SHA-512", fakePlan)(
        _.algorithm.name ==== "SHA-512",
        _.exportPath ==== null,
      )
    }

    "Concurrency parsing" >> {
      withParser("-c")() must throwAn[ExitException]("Missing value for concurrency, last argument was an alone '-c'")
      withParser("-c", "")() must throwAn[ExitException]("Empty value provided for concurrency")
      withParser("-c", "--")() must throwAn[ExitException]("Missing value for concurrency, next argument was the stop flag '--'")
      withParser("-c", fakePlan)() must throwAn[ExitException](s"Invalid concurrency setting: '$fakePlan', expecting a positive integer")
      withParser("-cxxx", fakePlan)() must throwAn[ExitException]("Invalid concurrency setting: 'xxx', expecting a positive integer")
      withParser("-c", "0", fakePlan)() must throwAn[ExitException]("Concurrency must be a positive integer, got: '0'")
      withParser("-c", "-12", fakePlan)() must throwAn[ExitException]("Concurrency must be a positive integer, got: '-12'")
      withParser("-c", "123", fakePlan)(
        _.concurrency ==== 123,
        _.exportPath ==== null,
      )
      withParser("-c1234", "--", fakePlan)(
        _.concurrency ==== 1234,
        _.exportPath ==== null,
      )
      withParser("-c", "1", "-c", "12345", fakePlan)(
        _.concurrency ==== 12345,
        _.exportPath ==== null,
      )
    }

    "Verification parsing" >> {
      withParser("-v")() must throwAn[ExitException]("Missing value for verification, last argument was an alone '-v'")
      withParser("-v", "")() must throwAn[ExitException]("Empty value provided for verification")
      withParser("-v", "--")() must throwAn[ExitException]("Missing value for verification, next argument was the stop flag '--'")
      withParser("-v", fakePlan)() must throwAn[ExitException](s"Unknown verification: '$fakePlan', supported verifications are: off, warn, require")
      withParser("-vxxx", fakePlan)() must throwAn[ExitException]("Unknown verification: 'xxx', supported verifications are: off, warn, require")
      withParser("-v", "warn", fakePlan)(
        _.verification ==== Verification.WARN,
        _.exportPath ==== null,
      )
      withParser("-vWaRn", "--", fakePlan)(
        _.verification ==== Verification.WARN,
        _.exportPath ==== null,
      )
      withParser("-v", "warn", "-v", "require", fakePlan, fakeExport)(
        _.verification ==== Verification.REQUIRE,
        _.exportPath ==== fakeExport,
      )
    }

    "Verification 'require' demands an export argument" >> {
      withParser("-vrequire", fakePlan)() must
        throwA[ExitException]("""\[verification\] is set to 'require', but \[export file\] was not provided""")
    }

    "Complex additional options parsing with overrides" >> {
      withParser("-l", "off", "-a", "SHA-256", "-c", "2", "-aGIT", "-v", "warn", fakePlan)(
        _.logLevel ==== Logger.Level.OFF,
        _.algorithm.name ==== "Git",
        _.concurrency ==== 2,
        _.verification ==== Verification.WARN,
        _.hashPlanPath ==== fakePlan,
        _.exportPath ==== null,
      )
      withParser("-vWaRn", "-a", "MD5", "-c123", "-l", "ERROR", "-v", "Require", "-aSHA-384", "--", "-aplan", "-vexport")(
        _.logLevel ==== Logger.Level.ERROR,
        _.algorithm.name ==== "SHA-384",
        _.concurrency ==== 123,
        _.verification ==== Verification.REQUIRE,
        _.hashPlanPath ==== "-aplan",
        _.exportPath ==== "-vexport",
      )
    }
  }

  "Too many arguments" >> {
    withParser(fakePlan, fakeExport, "xyzzy")() must
      throwA[ExitException]("""There are too many arguments provided after \[hash plan file\] and \[export file\], first was: 'xyzzy'""")
  }

  "No algorithms check" >> {
    val providers = Security.getProviders()
    try {
      for (provider <- providers) {
        // Can prolly cause some flip-flops in CI tests.
        // Using `sequential` so that this is the last test in the suite.
        // Perhaps the test is not worth-it and can remain as a commented out expectation
        Security.removeProvider(provider.getName)
      }
      withParser()() must throwAn[ExitException]("Algorithm 'SHA-1' is not supported. Supported algorithms: <none>")
    } finally {
      for (provider <- providers) {
        Security.addProvider(provider)
      }
    }
  }
}
