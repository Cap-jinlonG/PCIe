package PCIe
import chisel3._
import chisel3.util._
import freechips.rocketchip.util._
import scala.collection.immutable.Nil
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tilelink.TLMessages._
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.amba.axi4._
import utility._
import freechips.rocketchip.util._
import freechips.rocketchip.util.MaskGen


class writeEntry() extends Bundle with pcieParameters with TL1BundleParameters {
    val wvalid = Bool()
    val wready = Bool()
    val waddr = UInt(addrBits.W)
    val respStatus  = UInt(2.W)
    val wstatus = UInt(2.W)
    val entryid = UInt(ridBits.W)
    val awid = UInt(aridBits.W)
    val entryFifoid =UInt(ridBits.W)
//    val sameAxiFifoid = UInt(ridBits.W)
    val validFifoId = UInt(ridBits.W)
    val waitWFifoId = UInt(ridBits.W)
    val wsize = UInt(log2Ceil(tldataBits).W)
    val isLast = Bool()
    val count = UInt(ridBits.W)
    val data = UInt(tldataBits.W)
    val mask = UInt((tldataBits/8).W)
    val validStatus = UInt(2.W)
    val size = UInt(axiSizeBits.W)
    val len = UInt(axiLenBits.W)


}


class WriteStack(entries : Int = 32) extends Module{
    val io = IO(
      new Bundle(){
        val axiWriteReq_in = new AXIWriterRequestBundle
        val axiMenWriteReq_out = new MenAXIReqestBundle
        val axiMenWriteResp_in = new MenAXIRespBundle
        val axiWriteResp_out = new AXIWriteRespBundle
        val validReq_in = DecoupledIO(new validReqBundle())
        val validResp_out = Flipped(DecoupledIO(new validRespBundle()))

      }
    )
  /*
     Data Structure:
       writeStack: 32 entrires
   */
  val writeStack = RegInit(VecInit(Seq.fill(entries)(0.U.asTypeOf(new writeEntry))))

  val idel::waitW::sending::sendW::waitBResp::done::Nil = Enum(6)
  val idel1::waitValid::waitValidResp::validdone::Nil =Enum(4)
  val wreqArb = Module(new Arbiter(new writeEntry,entries))
  val awreqArb = Module(new Arbiter(new writeEntry,entries))
  val validreqArb = Module(new Arbiter(new writeEntry,entries))

  //flags
  val full = Cat(writeStack.map(_.wvalid)).andR
  val alloc = !full && io.axiWriteReq_in.aw.valid
  io.axiWriteReq_in.aw.ready := !full
  val idxInsert = Mux(alloc,PriorityEncoder(writeStack.map(!_.wvalid)),0.U)
  //when write stack is not full,can receive aw request
  when(alloc){

    val entry = writeStack(idxInsert)
    entry.wvalid  := true.B
    entry.waddr := io.axiWriteReq_in.aw.bits.addr
    entry.wstatus := waitW
    entry.validStatus := waitValid
    entry.wsize := OH1ToUInt(io.axiWriteReq_in.aw.bits.bytes1())
    entry.size := io.axiWriteReq_in.aw.bits.size
    entry.len := io.axiWriteReq_in.aw.bits.len
    entry.awid := io.axiWriteReq_in.aw.bits.id
    entry.entryid := idxInsert
    //w nend be send to first req

//    entry.entryFifoid := PopCount(writeStack.map(e => e.wvalid && (e.wstatus === waitW)))
//    entry.waitWFifoId :=  writeStack.count(e => e.wvalid&&(e.wstatus === waitW ))
//    entry.sameAxiFifoid := writeStack.count(e => e.wvalid && e.awid === io.axiWriteReq_in.aw.bits.id)
//    entry.validFifoId := writeStack.count(e => e.wvalid && e.validStatus === waitValid )
  }

  //receive w request
  val canW = Cat(writeStack.map(e => e.wvalid && e.wstatus === waitW && e.waitWFifoId === 0.U)).orR
  io.axiWriteReq_in.w.ready := canW

  //update waitWFifoId
  when(io.axiWriteReq_in.aw.fire && io.axiWriteReq_in.w.fire)
      {
        val entry = writeStack(idxInsert)
        entry.waitWFifoId := PopCount(writeStack.map(e => e.wvalid&&(e.wstatus === waitW ))) - 1.U
      }.elsewhen(io.axiWriteReq_in.aw.fire && !io.axiWriteReq_in.w.fire)
      {
        val entry = writeStack(idxInsert)
        entry.waitWFifoId := PopCount(writeStack.map(e => e.wvalid&&(e.wstatus === waitW )))
      }

  when(io.axiWriteReq_in.w.fire)
    {
          writeStack foreach{
            e =>
              when(e.wvalid && e.wstatus === waitW &&e.waitWFifoId === 0.U){
                e.data := io.axiWriteReq_in.w.bits.data
                e.mask := io.axiWriteReq_in.w.bits.strb
                e.wstatus := sending
                e.waitWFifoId := e.waitWFifoId-1.U
              }.elsewhen(e.wvalid&&(e.wstatus === waitW))
              {
                e.waitWFifoId := e.waitWFifoId-1.U
              }
          }
    }

  //when entry is wvalid and waitValid , send request to valid this block
  val hasValid = Cat(writeStack.map(e =>  e.validStatus === waitValid && e.validFifoId === 0.U)).orR
//  validreqArb.io.out.valid := hasValid
  validreqArb.io.in zip writeStack foreach {
      case(in,e) =>
        in.valid := (e.validStatus === waitValid && e.validFifoId === 0.U && e.wvalid)
        in.bits := e
  }

  io.validReq_in.bits.addr := validreqArb.io.out.bits.waddr
  io.validReq_in.bits.source := Cat(validreqArb.io.out.bits.entryid, validreqArb.io.out.bits.awid)
//  e.wstatus := waitValidResp
  val chosen = validreqArb.io.chosen

  validreqArb.io.out.ready := io.validReq_in.ready
  io.validReq_in.valid := validreqArb.io.out.valid
  //update validFifo
  when(io.axiWriteReq_in.aw.fire && io.validReq_in.fire)
      {
        val entry = writeStack(idxInsert)
        entry.validFifoId := PopCount(writeStack.map(e => e.wvalid&&(e.validStatus === waitValid ))) - 1.U
      }.elsewhen(io.axiWriteReq_in.aw.fire && !io.validReq_in.fire)
      {
        val entry = writeStack(idxInsert)
        entry.validFifoId := PopCount(writeStack.map(e => e.wvalid && (e.validStatus === waitValid)))
      }
  when(io.validReq_in.fire)
    {
        writeStack foreach {
          e =>
            when(e.wvalid && e.validStatus === waitValid && e.validFifoId === 0.U) {
              e.validStatus := waitValidResp
              e.validFifoId := e.validFifoId - 1.U
            }.elsewhen(e.wvalid && e.validStatus === waitValid) {
              e.validFifoId := e.validFifoId - 1.U
            }
        }
    }

  //receive valid response
  //
  val canValid = Cat(writeStack.map(e => e.wvalid && e.validStatus === waitValidResp)).orR
  io.validResp_out.ready := canValid
  when(io.validResp_out.fire)
    {
      val rspIdx = io.validResp_out.bits.source(18,14).asUInt
      val entry = writeStack(rspIdx)
      entry.validStatus := validdone
    }


  //when entry is validdone and sending ,it can send put to memory
  val canAW = Cat(writeStack.map(e => e.wvalid && e.wstatus === sending &&e.validStatus === validdone && e.entryFifoid === 0.U)).orR
//  wreqArb.io.out.valid := canAW

  wreqArb.io.in zip writeStack foreach{
        case(in,e) =>
        in.valid := e.wvalid && e.wstatus === sending &&e.validStatus === validdone && e.entryFifoid === 0.U
        in.bits := e
  }
  io.axiMenWriteReq_out.aw.valid := wreqArb.io.out.valid
  io.axiMenWriteReq_out.aw.bits.id := wreqArb.io.out.bits.awid
  io.axiMenWriteReq_out.aw.bits.addr := wreqArb.io.out.bits.waddr
  io.axiMenWriteReq_out.aw.bits.size := wreqArb.io.out.bits.size
  io.axiMenWriteReq_out.aw.bits.len := wreqArb.io.out.bits.len
  io.axiMenWriteReq_out.aw.bits.burst := 0.U
  io.axiMenWriteReq_out.aw.bits.lock := 0.U
  io.axiMenWriteReq_out.aw.bits.prot := 0.U
  io.axiMenWriteReq_out.aw.bits.qos := 0.U
  io.axiMenWriteReq_out.aw.bits.cache := 0.U
  wreqArb.io.out.ready := io.axiMenWriteReq_out.aw.ready
  //update entry fifoid
  when(io.axiWriteReq_in.aw.fire && io.axiMenWriteReq_out.w.fire)
  {
    val entry = writeStack(idxInsert)
    entry.entryFifoid := PopCount(writeStack.map(e => e.wvalid && (e.wstatus === waitW || e.wstatus === sending || e.wstatus === sendW))) - 1.U
  }.elsewhen(io.axiWriteReq_in.aw.fire && !io.axiMenWriteReq_out.w.fire)
  {
    val entry = writeStack(idxInsert)
    entry.entryFifoid := PopCount(writeStack.map(e => e.wvalid && (e.wstatus === waitW || e.wstatus === sending || e.wstatus === sendW)))
  }
  when(io.axiMenWriteReq_out.aw.fire)
    {
        writeStack foreach{
          e =>
              when(e.wvalid && e.wstatus === sending &&e.validStatus === validdone && e.entryFifoid === 0.U )
                {
//                    io.axiMenWriteReq_out.aw.bits.user :=
                    e.wstatus := sendW
                }
        }
    }
  val canMenW = Cat(writeStack.map(e => e.wvalid && e.wstatus === sendW && e.validStatus === validdone && e.entryFifoid === 0.U)).orR
//  io.axiMenWriteReq_out.w.valid := canMenW
    awreqArb.io.in zip writeStack foreach {
      case (in, e) =>
        /*
            when entry is valid and is the first beat
         */
        in.valid := (e.wvalid && e.wstatus === sendW && e.validStatus === validdone && e.entryFifoid === 0.U)
        in.bits := e
    }
  io.axiMenWriteReq_out.w.bits.data := awreqArb.io.out.bits.data
  io.axiMenWriteReq_out.w.bits.last := true.B
  io.axiMenWriteReq_out.w.bits.strb := awreqArb.io.out.bits.mask
  io.axiMenWriteReq_out.w.valid := awreqArb.io.out.valid
  awreqArb.io.out.ready := io.axiMenWriteReq_out.w.ready
  when(io.axiMenWriteReq_out.w.fire)
  {
      writeStack foreach{
          e =>
            when(e.wvalid && e.wstatus === sendW && e.validStatus === validdone && e.entryFifoid === 0.U)
              {

                  e.wstatus := done
                  e.wvalid := false.B
                  e.validStatus := idel
                  e.entryFifoid := e.entryFifoid - 1.U
              }.elsewhen(e.wvalid  && e.entryFifoid =/= 0.U)
              {
                  e.entryFifoid := e.entryFifoid - 1.U
              }
      }
  }

  //receive a b resp
  val hasWaitBResp = Cat(writeStack.map(e => e.wvalid && e.wstatus === waitBResp )).orR
  io.axiMenWriteResp_in.b.ready := hasWaitBResp

  //receive b resp and send
  io.axiWriteResp_out.b.valid := io.axiMenWriteResp_in.b.valid
  io.axiMenWriteResp_in.b.ready := io.axiWriteResp_out.b.ready
  io.axiWriteResp_out.b.bits := io.axiMenWriteResp_in.b.bits

}
