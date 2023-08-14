package PCIe
import chisel3._
import chisel3.util._


trait pcieParameters {
  val addrBits = 35
  val ridBits = 5
  val aridBits = 14
  val entrydatabits = 256
  val beatBytes = 8
  val fifoBits = 6
  val axiSizeBits = 3
  val axiLenBits = 8
//  val entries = 32
}
trait AXIBundelParameters{
//  val addrBits = 35
  val axidataBits = 256
  val idBits = 14

}
trait TL1BundleParameters{
  val addressBits = 35
  val tldataBits = 256
  val sourceBits = 19
  val sinkBits = 5
  val sizeBits = 3

}