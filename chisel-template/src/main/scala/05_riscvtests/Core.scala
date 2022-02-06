package decode_more

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
  val regFile = Mem(32, UInt(WORD_LEN.W)) // レジスタ

  // IFステージ
  /* ******************************** */
  val pc = RegInit(0.U(WORD_LEN.W))
  // pcを4ずつカウントアップ
  pc := pc + 4.U(WORD_LEN.W)
  // pcの値のアドレスにある命令をフェッチしてくる
  io.imem.addr := pc


  // IDステージ
  /* ******************************** */
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
  // B形式の即値
  val imm_b = Cat(io.imem.inst(31), io.imem.inst(7), io.imem.inst(30, 25), io.imem.inst(11, 8))
  val imm_b_sext = Cat(Fill(19, imm_b(11)), imm_b, 0.U(1.W))
  // J形式
  val imm_j = Cat(io.imem.inst(31), io.imem.inst(19, 12), io.imem.inst(20), io.imem.inst(30, 21))
  val imm_j_sext = Cat(Fill(11, imm_j(19)), imm_j, 0.U(1.W))

  val cSignals = ListLookup(io.imem.inst,
    // デフォルト値はALU_X
    List(ALU_X, 0.U(WORD_LEN.W), 0.U(WORD_LEN.W)),
    Array(
      // io.imem.inst が各命令のBitPat(ADDとか)と一致したら
      // List(exeFcn, op1Data, op2Data) に各Listの中身を渡す
      LW -> List(ALU_ADD, rs1Data, imm_i_sext),
      SW -> List(ALU_ADD, rs1Data, imm_s_sext),
      // 算術演算
      ADD -> List(ALU_ADD, rs1Data, rs2Data),
      ADDI -> List(ALU_ADD, rs1Data, imm_i_sext),
      SUB -> List(ALU_SUB, rs1Data, rs2Data),
      // 論理演算
      AND -> List(ALU_AND, rs1Data, rs2Data),
      OR -> List(ALU_OR, rs1Data, rs2Data),
      XOR -> List(ALU_XOR, rs1Data, rs2Data),
      ANDI -> List(ALU_AND, rs1Data, imm_i_sext),
      ORI -> List(ALU_OR, rs1Data, imm_i_sext),
      XORI -> List(ALU_XOR, rs1Data, imm_i_sext),
      // シフト演算
      SLL -> List(ALU_SLL, rs1Data, rs2Data),
      SRL -> List(ALU_SRL, rs1Data, rs2Data),
      SRA -> List(ALU_SRA, rs1Data, rs2Data),
      SLLI -> List(ALU_SLL, rs1Data, imm_i(4,0)),
      SRLI -> List(ALU_SRL, rs1Data, imm_i(4,0)),
      SRAI -> List(ALU_SRA, rs1Data, imm_i(4,0)),
      // 比較
      SLT -> List(ALU_SLL, rs1Data, rs2Data),
      SLTU -> List(ALU_SLTU, rs1Data, rs2Data),
      SLTI -> List(ALU_SLT, rs1Data, imm_i_sext),
      SLTIU -> List(ALU_SLTU, rs1Data, imm_i_sext),
      // 分岐
      BEQ -> List(BR_BEQ, rs1Data, rs2Data),
      BNE -> List(BR_BNE, rs1Data, rs2Data),
      BLT -> List(BR_BLT, rs1Data, rs2Data),
      BGE -> List(BR_BGE, rs1Data, rs2Data),
      BLTU -> List(BR_BLTU, rs1Data, rs2Data),
      BGEU -> List(BR_BGEU, rs1Data, rs2Data),
      // ジャンプ
      JAL -> List(ALU_ADD, pc, imm_j_sext),
      JALR -> List(ALU_JALR, rs1Data, imm_j_sext),
    )
  )
  // cSignalsのListを左から順番に各要素に代入
  val exeFcn::op1Data::op2Data::Nil = cSignals


  // EXステージ
  /* ******************************** */
  val aluOut = MuxCase(regFile(rdAddr), Seq(
    (exeFcn === ALU_ADD) -> (op1Data + op2Data),
    (exeFcn === ALU_SUB) -> (op1Data - op2Data),
    (exeFcn === ALU_AND) -> (op1Data & op2Data),
    (exeFcn === ALU_OR) -> (op1Data | op2Data),
    (exeFcn === ALU_XOR) -> (op1Data ^ op2Data),
    // なんかココらへんのUIntとかの変換が闇
    (exeFcn === ALU_SLL) -> (op1Data << op2Data(4, 0)),
    (exeFcn === ALU_SRL) -> (op1Data.asUInt() >> op2Data(4, 0)),
    (exeFcn === ALU_SRA) -> (op1Data.asSInt() >> op2Data(4, 0)).asUInt(),
    (exeFcn === ALU_SLT) -> (op1Data.asSInt() < op2Data.asSInt()),
    (exeFcn === ALU_SLTU) -> (op1Data.asUInt() < op2Data.asUInt()),
    // ifがtrueなら左に分岐，falseなら右に分岐
    (exeFcn === BR_BEQ) -> Mux(op1Data === op2Data, imm_b_sext, 0.U(WORD_LEN.W)),
    (exeFcn === BR_BNE) -> Mux(op1Data =/= op2Data, imm_b_sext, 0.U(WORD_LEN.W)),
    (exeFcn === BR_BLT) -> Mux(op1Data < op2Data, imm_b_sext, 0.U(WORD_LEN.W)),
    (exeFcn === BR_BGE) -> Mux(op1Data >= op2Data, imm_b_sext, 0.U(WORD_LEN.W)),
    (exeFcn === BR_BLTU) -> Mux(op1Data.asUInt() < op2Data.asUInt(), imm_b_sext, 0.U(WORD_LEN.W)),
    (exeFcn === BR_BGEU) -> Mux(op1Data.asUInt() >= op2Data.asUInt(), imm_b_sext, 0.U(WORD_LEN.W)),
    (exeFcn === ALU_JALR) -> ((op1Data + op2Data) & ~1.U(WORD_LEN.W)),
  ))
  io.dmem.addr := aluOut // LWやらSWやらするデータメモリのアドレス
    
  // MAステージ
  /* ******************************** */
  // SW命令なら書き込み信号などを設定
  io.dmem.wEn := false.B
  when(io.imem.inst === SW){
    io.dmem.wEn := true.B // SWならメモリへの書き込みenableをtrue
  }
  io.dmem.wData := rs2Data


  // WBステージ
  /* ******************************** */
  regFile(rdAddr) := MuxCase(aluOut, Seq( // デフォはALUの結果をレジスタに入れる
    (io.imem.inst === LW) -> io.dmem.data, // LWしたALUのアドレスにあるメモリデータをレジスタに書き込み
    ((io.imem.inst === JAL) || (io.imem.inst === JALR)) -> pc, // ジャンプならrdにpc+4を入れる
  ))
  // 分岐命令ならpcに書き込み
  when((io.imem.inst === BEQ) || (io.imem.inst === BNE)|| (io.imem.inst === BLT) ||
    (io.imem.inst === BGE) || (io.imem.inst === BLTU) || (io.imem.inst === BGEU)){
    pc := pc + aluOut
  }
  when((io.imem.inst === JAL) || (io.imem.inst === JALR)){
    pc := aluOut
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