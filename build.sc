// import Mill dependency
import mill._
import mill.define.Sources
import mill.modules.Util
import mill.scalalib.TestModule.ScalaTest
import scalalib._
// support BSP
import mill.bsp._
import scalafmt._
import os.Path
import publish._
import $file.`rocket-chip`.common
import $file.`rocket-chip`.`api-config-chipsalliance`.`build-rules`.mill.build
import $file.`rocket-chip`.hardfloat.build
object ivys {
  val sv = "2.12.13"
  val chisel3 = ivy"edu.berkeley.cs::chisel3:3.5.5"
  val chisel3Plugin = ivy"edu.berkeley.cs:::chisel3-plugin:3.5.5"
  val chiseltest = ivy"edu.berkeley.cs::chiseltest:0.5.5"
  val scalatest = ivy"org.scalatest::scalatest:3.2.2"
  val macroParadise = ivy"org.scalamacros:::paradise:2.1.1"
}
val defaultVersions = Map(
  "chisel3" -> "3.5.0",
  "chisel3-plugin" -> "3.5.0",
  "chiseltest" -> "0.3.2",
  "scala" -> "2.12.13",
  "scalatest" -> "3.2.7"
)
def getVersion(dep: String, org: String = "edu.berkeley.cs", cross: Boolean = false) = {
  val version = sys.env.getOrElse(dep + "Version", defaultVersions(dep))
  if (cross)
    ivy"$org:::$dep:$version"
  else
    ivy"$org::$dep:$version"
}
trait CommonModule extends ScalaModule {
  override def scalaVersion = defaultVersions("scala")

  override def scalacOptions = Seq("-Xsource:2.11")

  val macroParadise = ivy"org.scalamacros:::paradise:2.1.1"
  val chisel3Plugin = ivy"edu.berkeley.cs:::chisel3-plugin:3.5.0"

  override def compileIvyDeps = Agg(macroParadise)
  override def scalacPluginIvyDeps = Agg(macroParadise, chisel3Plugin)

}

object rocketchip extends `rocket-chip`.common.CommonRocketChip {

  val rcPath = os.pwd / "rocket-chip"

  override def scalaVersion = ivys.sv

  override def scalacOptions = Seq("-Xsource:2.11")

  override def millSourcePath = rcPath

  object configRocket extends `rocket-chip`.`api-config-chipsalliance`.`build-rules`.mill.build.config with PublishModule {
    override def millSourcePath = rcPath / "api-config-chipsalliance" / "design" / "craft"

    override def scalaVersion = T {
      rocketchip.scalaVersion()
    }

    override def pomSettings = T {
      rocketchip.pomSettings()
    }

    override def publishVersion = T {
      rocketchip.publishVersion()
    }
  }

  object hardfloatRocket extends `rocket-chip`.hardfloat.build.hardfloat {
    override def millSourcePath = rcPath / "hardfloat"

    override def scalaVersion = T {
      rocketchip.scalaVersion()
    }

    def chisel3IvyDeps = if(chisel3Module.isEmpty) Agg(
      common.getVersion("chisel3")
    ) else Agg.empty[Dep]

    def chisel3PluginIvyDeps = Agg(common.getVersion("chisel3-plugin", cross=true))
  }

  def hardfloatModule = hardfloatRocket

  def configModule = configRocket

}
object utility extends SbtModule with ScalafmtModule with CommonModule {

  override def ivyDeps = Agg(common.getVersion("chisel3"))

  override def millSourcePath = os.pwd / "utility"

  override def moduleDeps = super.moduleDeps ++ Seq(rocketchip)
}

object PCIe extends SbtModule with ScalafmtModule with CommonModule {
  m =>
  override def millSourcePath = millOuterCtx.millSourcePath


  override def ivyDeps = super.ivyDeps() ++ Agg(
    getVersion("chisel3"),
    getVersion("chiseltest"),
  )

  override def moduleDeps = super.moduleDeps ++ Seq(rocketchip, utility)

  object test extends Tests {
    override def ivyDeps = super.ivyDeps() ++ Agg(
      getVersion("scalatest", "org.scalatest")
    )

    def testFramework = "org.scalatest.tools.Framework"
  }
}