package lw

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
    val rs2Data = Mux(rs1Addr =/= 0.U(WORD_LEN.W), regFile(rs2Addr), 0.U(WORD_LEN.W))
  
    // LW系の即値を抜き出す
    val imm_i = io.imem.inst(31, 20)
    // 32bitのうちimm_iの先頭bitを20個拡張して，下12個はimm_i
    val imm_i_sext = Cat(Fill(20, imm_i(11)), imm_i)

    val aluOut = Mux(io.imem.inst === LW, rs1Data + imm_i_sext, 0.U(WORD_LEN.W))
    io.dmem.addr := aluOut

    regFile(rdAddr) := io.dmem.data
  
  //**********************************
  // Debug
  io.exit := (io.imem.inst === 0x14131211.U(WORD_LEN.W))
  printf("---------\n")
  printf(p"pc : 0x${Hexadecimal(pc)}\n")
  printf(p"inst : 0x${Hexadecimal(io.imem.inst)}\n")
  printf(p"rs1_addr : $rs1Addr\n")
  printf(p"rs2_addr : $rs2Addr\n")
  printf(p"rd_addr : $rdAddr\n")
  printf(p"rs1Data : 0x${Hexadecimal(rs1Data)}\n")
  printf(p"rs2Data : 0x${Hexadecimal(rs2Data)}\n")
  printf(p"imm_i_sext : 0x${Hexadecimal(imm_i_sext)}\n")
  printf(p"aluOut : 0x${Hexadecimal(aluOut)}\n")
  printf(p"io.dmem.data : 0x${Hexadecimal(io.dmem.data)}\n")
  printf(p"regRdData : 0x${Hexadecimal(regFile(rdAddr))}\n")
  printf("---------\n")

}