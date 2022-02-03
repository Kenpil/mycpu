package sw

import chisel3._
import chisel3.util._
import common.Consts._
import common.Instructions._

class Core extends Module {
    val io = IO(new Bundle{
        val imem = Flipped(new iMemory())
        val dmem = Flipped(new dMemory())
        val exit = Output(Bool())
    })

    val regFile = Mem(32, UInt(WORD_LEN.W))

    val pc = RegInit(0.U(WORD_LEN.W))
    // pcを4ずつカウントアップ
    pc := pc + 4.U(WORD_LEN.W)
    io.imem.addr := pc

    // 配線としてinstから各要素を抜き出す
    val rs1Addr = io.imem.inst(19, 15)
    val rs2Addr = io.imem.inst(24, 20)
    val rdAddr = io.imem.inst(11, 7)
    // アドレスが非ゼロならレジスタから読み取り，ゼロなら何もせん
    val rs1Data = Mux(rs1Addr =/= 0.U(WORD_LEN.W), regFile(rs1Addr), 0.U(WORD_LEN.W))
    val rs2Data = Mux(rs2Addr =/= 0.U(WORD_LEN.W), regFile(rs2Addr), 0.U(WORD_LEN.W))
  
    // I形式(LWとか)の即値を抜き出す
    val imm_i = io.imem.inst(31, 20)
    // 32bitのうちimm_iの先頭bitを20個拡張して，下12個はimm_i
    val imm_i_sext = Cat(Fill(20, imm_i(11)), imm_i)
    // S形式(SWとか)の即値を抜き出す
    val imm_s = Cat(io.imem.inst(31, 25), io.imem.inst(11, 7))
    // 32bitのうちimm_sの先頭bitを20個拡張して，下12個はimm_i
    val imm_s_sext = Cat(Fill(20, imm_s(11)), imm_s)

    val aluOut = MuxCase(0.U(WORD_LEN.W), Seq(
        (io.imem.inst === LW) -> (rs1Data + imm_i_sext), // LWするメモリのアドレス
        (io.imem.inst === SW) -> (rs1Data + imm_s_sext) // SWするメモリのアドレス
    ))
    io.dmem.addr := aluOut // LWやらSWやらするメモリのアドレス

    regFile(rdAddr) := io.dmem.data

    // SW命令なら書き込み信号などを設定
    when(io.imem.inst === SW){
      io.dmem.wEn := true.B
      io.dmem.wData := rs2Data
    }.otherwise{
      io.dmem.wEn := false.B
      io.dmem.wData := 0.U(WORD_LEN.W)
    }
  
  //**********************************
  // Debug
  io.exit := (io.imem.inst === 0x00602823.U(WORD_LEN.W))
  printf("---------\n")
  printf(p"pc : 0x${Hexadecimal(pc)}\n")
  printf(p"inst : 0x${Hexadecimal(io.imem.inst)}\n")
  printf(p"rs1_addr : $rs1Addr\n")
  printf(p"rs2_addr : $rs2Addr\n")
  printf(p"rd_addr : $rdAddr\n")
  printf(p"rs1Data : 0x${Hexadecimal(rs1Data)}\n")
  printf(p"rs2Data : 0x${Hexadecimal(rs2Data)}\n")
  printf(p"imm_i_sext : 0x${Hexadecimal(imm_i_sext)}\n")
  printf(p"imm_s_sext : 0x${Hexadecimal(imm_i_sext)}\n")
  printf(p"aluOut : 0x${Hexadecimal(aluOut)}\n")
  printf(p"io.dmem.wEn : ${io.dmem.wEn}\n")
  printf(p"io.dmem.wData : 0x${Hexadecimal(io.dmem.wData)}\n")
  printf("---------\n")

}