package PCIe
import chisel3._
import chisel3.{Bool, Bundle, Flipped, UInt}
import chisel3.util.DecoupledIO
import freechips.rocketchip.amba.axi4.{AXI4BundleAR, AXI4BundleAW, AXI4BundleB, AXI4BundleParameters, AXI4BundleR, AXI4BundleW}
import freechips.rocketchip.tilelink.{TLBundleA, TLBundleD, TLBundleParameters}


class PEIeInBundle extends Bundle with AXIBundelParameters with TL1BundleParameters with pcieParameters {
      val ar = Flipped(DecoupledIO(
        new AXI4BundleAR(new AXI4BundleParameters(
          addrBits = addrBits,
          dataBits = axidataBits,
          idBits = idBits
        ))
      ))
      val aw = Flipped(DecoupledIO(
        new AXI4BundleAW(new AXI4BundleParameters(
          addrBits = addrBits,
          dataBits = axidataBits,
          idBits = idBits
        ))
      ))
      val w = Flipped(
        DecoupledIO(
          new AXI4BundleW(new AXI4BundleParameters(
            addrBits = addrBits,
            dataBits = axidataBits,
            idBits = idBits
          ))
        ))
    val d = Flipped(DecoupledIO(new TLBundleD(new TLBundleParameters(
      addressBits = addressBits, dataBits = tldataBits, sourceBits = sourceBits, sinkBits = sinkBits, sizeBits = sizeBits
      , echoFields = Nil, requestFields = Nil, responseFields = Nil, hasBCE = false
    ))))
    val validResp_out = Flipped(DecoupledIO(new validRespBundle()))

    val b = Flipped(DecoupledIO(
      new AXI4BundleB(
        new AXI4BundleParameters(
          addrBits = addrBits,
          dataBits = axidataBits,
          idBits = idBits
        )
      )
    ))
//    val r = Flipped(DecoupledIO(
//      new AXI4BundleR(
//        new AXI4BundleParameters(
//          addrBits = addrBits,
//          dataBits = dataBits,
//          idBits = idBits
//        )
//      )
//    ))
}
class PCIeOutBundle extends Bundle with AXIBundelParameters with TL1BundleParameters with  pcieParameters {
  val b = DecoupledIO(
    new AXI4BundleB(
      new AXI4BundleParameters(
        addrBits = addrBits,
        dataBits = axidataBits,
        idBits = idBits
      )
    )
  )
  val r = DecoupledIO(
    new AXI4BundleR(
      new AXI4BundleParameters(
        addrBits = addrBits,
        dataBits = axidataBits,
        idBits = idBits
      )
    )
  )
  val validReq_in = DecoupledIO(new validReqBundle())
  val aw = DecoupledIO(
    new AXI4BundleAW(new AXI4BundleParameters(
      addrBits = addrBits,
      dataBits = axidataBits,
      idBits = idBits
    ))
  )
  val w = DecoupledIO(
      new AXI4BundleW(new AXI4BundleParameters(
        addrBits = addrBits,
        dataBits = axidataBits,
        idBits = idBits
      ))
    )
  val a = DecoupledIO(new TLBundleA(new TLBundleParameters(
    addressBits = addressBits, dataBits = tldataBits, sourceBits = sourceBits, sinkBits = sinkBits, sizeBits = sizeBits
    , echoFields = Nil, requestFields = Nil, responseFields = Nil, hasBCE = false
  )))

}


class MenAXIReqestBundle extends Bundle with AXIBundelParameters with pcieParameters {
  val aw = DecoupledIO(
    new AXI4BundleAW(new AXI4BundleParameters(
      addrBits = addrBits,
      dataBits = axidataBits,
      idBits = idBits
    ))
  )
  val w = DecoupledIO(
    new AXI4BundleW(new AXI4BundleParameters(
      addrBits = addrBits,
      dataBits = axidataBits,
      idBits = idBits
    ))
  )
}
class MenAXIRespBundle extends Bundle with  AXIBundelParameters with pcieParameters {
  val b = Flipped(
    DecoupledIO(
      new AXI4BundleB(new AXI4BundleParameters(
        addrBits = addrBits,
        dataBits = axidataBits,
        idBits = idBits))))
}

class AXIWriterRequestBundle extends  Bundle with AXIBundelParameters with pcieParameters {
  val aw = Flipped(DecoupledIO(
    new AXI4BundleAW(new AXI4BundleParameters(
      addrBits = addrBits,
      dataBits = axidataBits,
      idBits = idBits
    ))
  ))
  val w = Flipped(
    DecoupledIO(
      new AXI4BundleW(new AXI4BundleParameters(
        addrBits = addrBits,
        dataBits = axidataBits,
        idBits = idBits
      ))
    ))
}
class AXIWriteRespBundle extends Bundle  with  AXIBundelParameters with pcieParameters{
  val b =DecoupledIO(
    new AXI4BundleB(
      new AXI4BundleParameters(
        addrBits = addrBits,
        dataBits = axidataBits,
        idBits = idBits
      )
    )
  )
}

class validReqBundle extends Bundle with pcieParameters with TL1BundleParameters {
  //    val validStatus = Bool()
  val addr  = UInt(addrBits.W)
  val source = UInt(sourceBits.W)
}
class validRespBundle extends Bundle with pcieParameters  with TL1BundleParameters
{
  val source = UInt(sourceBits.W)
  val validStatus = Bool()
}

class TLRequestBundel extends Bundle with TL1BundleParameters {
  val a = DecoupledIO(new TLBundleA(new TLBundleParameters(
    addressBits = addressBits, dataBits = tldataBits, sourceBits = sourceBits, sinkBits = sinkBits, sizeBits = sizeBits
    , echoFields = Nil, requestFields = Nil, responseFields = Nil, hasBCE = false
  )))
}

class AXIRequestBundel extends Bundle with AXIBundelParameters with pcieParameters {
  val ar = Flipped(DecoupledIO(
    new AXI4BundleAR(new AXI4BundleParameters(
      addrBits = addrBits,
      dataBits = axidataBits,
      idBits = idBits
    ))
  ))


}

class TLRespBundle extends Bundle() with TL1BundleParameters {
  val d = Flipped(DecoupledIO(new TLBundleD(new TLBundleParameters(
    addressBits = addressBits, dataBits = tldataBits, sourceBits = sourceBits, sinkBits = sinkBits, sizeBits = sizeBits
    , echoFields = Nil, requestFields = Nil, responseFields = Nil, hasBCE = false
  ))))
}

class AXIRespBundle extends Bundle() with AXIBundelParameters with pcieParameters {
  val r = DecoupledIO(
    new AXI4BundleR(
      new AXI4BundleParameters(
        addrBits = addrBits,
        dataBits = axidataBits,
        idBits = idBits
      )
    )
  )
}
