package riscvtests

import chisel3._
import chisel3.util._
import common.Consts._
import common.Instructions._

class Core extends Module {
  val io = IO(new Bundle{
    val imem = Flipped(new iMemory())
    val dmem = Flipped(new dMemory())
    val exit = Output(Bool())
    val gp = Output(UInt(WORD_LEN.W))
  })
  val regFile = Mem(32, UInt(WORD_LEN.W)) // 32本のフツーのレジスタ
  val csrRegFile = Mem(4096, UInt(WORD_LEN.W)) // CSRレジスタ
  io.gp := regFile(3)


  // IFステージ
  /* ******************************** */
  val pc = RegInit(0.U(WORD_LEN.W))
  // pcを4ずつカウントアップ
  val pcPlus4 = pc + 4.U(WORD_LEN.W)
  // pcの値のアドレスにある命令をフェッチしてくる
  io.imem.addr := pc

  // デバッグ用のサイクルカウント
  val cnt = RegInit(0.U(WORD_LEN.W))
  cnt := cnt + 1.U(WORD_LEN.W)


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
  val imm_z = io.imem.inst(19, 15)
  // 32bitのうちimm_iの先頭bitを20個拡張して，下12個はimm_i
  val imm_i_sext = Cat(Fill(20, imm_i(11)), imm_i)
  val imm_z_uext = Cat(0.U(27.W), imm_z)
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
  // U形式
  val imm_u = io.imem.inst(31, 12)
  val imm_u_sext = imm_u << 12

  val cSignals = ListLookup(io.imem.inst,
    // デフォルト値はALU_X
    // io.imem.inst が各命令のBitPat(ADDとか)と一致したら
    // List(exeFcn, op1Data, op2Data, readP, wbP) に各Listの中身を渡す
    // exeFcn: ALUでの実行内容
    // op1Data: オペランド1
    // op2Data: オペランド2
    // readP: write back データの読み込み元
    // wbP: wbの書き込み先 
    List(ALU_X, 0.U(WORD_LEN.W), 0.U(WORD_LEN.W), RP_X, WP_X),
    Array(
      LW -> List(ALU_ADD, rs1Data, imm_i_sext, MEM_R, RD_W),
      SW -> List(ALU_ADD, rs1Data, imm_s_sext, RS2_R, MEM_W),
      // 算術演算
      ADD -> List(ALU_ADD, rs1Data, rs2Data, ALU_R, RD_W),
      ADDI -> List(ALU_ADD, rs1Data, imm_i_sext, ALU_R, RD_W),
      SUB -> List(ALU_SUB, rs1Data, rs2Data, ALU_R, RD_W),
      // 論理演算
      AND -> List(ALU_AND, rs1Data, rs2Data, ALU_R, RD_W),
      OR -> List(ALU_OR, rs1Data, rs2Data, ALU_R, RD_W),
      XOR -> List(ALU_XOR, rs1Data, rs2Data, ALU_R, RD_W),
      ANDI -> List(ALU_AND, rs1Data, imm_i_sext, ALU_R, RD_W),
      ORI -> List(ALU_OR, rs1Data, imm_i_sext, ALU_R, RD_W),
      XORI -> List(ALU_XOR, rs1Data, imm_i_sext, ALU_R, RD_W),
      // シフト演算
      SLL -> List(ALU_SLL, rs1Data, rs2Data, ALU_R, RD_W),
      SRL -> List(ALU_SRL, rs1Data, rs2Data, ALU_R, RD_W),
      SRA -> List(ALU_SRA, rs1Data, rs2Data, ALU_R, RD_W),
      SLLI -> List(ALU_SLL, rs1Data, imm_i(4,0), ALU_R, RD_W),
      SRLI -> List(ALU_SRL, rs1Data, imm_i(4,0), ALU_R, RD_W),
      SRAI -> List(ALU_SRA, rs1Data, imm_i(4,0), ALU_R, RD_W),
      // 比較
      SLT -> List(ALU_SLT, rs1Data, rs2Data, ALU_R, RD_W),
      SLTU -> List(ALU_SLTU, rs1Data, rs2Data, ALU_R, RD_W),
      SLTI -> List(ALU_SLT, rs1Data, imm_i_sext, ALU_R, RD_W),
      SLTIU -> List(ALU_SLTU, rs1Data, imm_i_sext, ALU_R, RD_W),
      // 分岐
      BEQ -> List(BR_BEQ, rs1Data, rs2Data, ALU_R, PC_W),
      BNE -> List(BR_BNE, rs1Data, rs2Data, ALU_R, PC_W),
      BLT -> List(BR_BLT, rs1Data, rs2Data, ALU_R, PC_W),
      BGE -> List(BR_BGE, rs1Data, rs2Data, ALU_R, PC_W),
      BLTU -> List(BR_BLTU, rs1Data, rs2Data, ALU_R, PC_W),
      BGEU -> List(BR_BGEU, rs1Data, rs2Data, ALU_R, PC_W),
      // ジャンプ
      JAL -> List(ALU_ADD, pc, imm_j_sext, ALU_R, JMP_W),
      JALR -> List(ALU_JALR, rs1Data, imm_i_sext, ALU_R, JMP_W), // imm_"i" に注意
      // 即値ロード
      LUI -> List(ALU_ADD, 0.U(WORD_LEN.W), imm_u_sext, ALU_R, RD_W),
      AUIPC -> List(ALU_ADD, pc, imm_u_sext, ALU_R, RD_W),
      // CSR命令
      CSRRW -> List(ALU_ADD, rs1Data, 0.U(WORD_LEN.W), ALU_R, CSR_W),
      CSRRWI -> List(ALU_ADD, imm_z_uext, 0.U(WORD_LEN.W), ALU_R, CSR_W),
      CSRRS -> List(CSR_RS, rs1Data, 0.U(WORD_LEN.W), ALU_R, CSR_W),
      CSRRSI -> List(CSR_RS, imm_z_uext, 0.U(WORD_LEN.W), ALU_R, CSR_W),
      CSRRC -> List(CSR_RC, rs1Data, 0.U(WORD_LEN.W), ALU_R, CSR_W),
      CSRRCI -> List(CSR_RC, imm_z_uext, 0.U(WORD_LEN.W), ALU_R, CSR_W),
      // ECALL
      ECALL -> List(ECL_W, 0.U(WORD_LEN.W), 0.U(WORD_LEN.W), RP_X, ECL_W),
    )
  )
  // cSignalsのListを左から順番に各要素に代入
  val exeFcn::op1Data::op2Data::readP::wbP::Nil = cSignals


  // EXステージ
  /* ******************************** */
  // デフォルトはrdの元のレジスタ値
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
    (exeFcn === BR_BEQ) -> Mux(op1Data === op2Data, pc + imm_b_sext, pcPlus4),
    (exeFcn === BR_BNE) -> Mux(op1Data =/= op2Data, pc + imm_b_sext, pcPlus4),
    (exeFcn === BR_BLT) -> Mux(op1Data.asSInt() < op2Data.asSInt(), pc + imm_b_sext, pcPlus4),
    (exeFcn === BR_BGE) -> Mux(op1Data.asSInt() >= op2Data.asSInt(), pc + imm_b_sext, pcPlus4),
    (exeFcn === BR_BLTU) -> Mux(op1Data.asUInt() < op2Data.asUInt(), pc + imm_b_sext, pcPlus4),
    (exeFcn === BR_BGEU) -> Mux(op1Data.asUInt() >= op2Data.asUInt(), pc + imm_b_sext, pcPlus4),
    (exeFcn === ALU_JALR) -> ((op1Data + op2Data) & ~1.U(WORD_LEN.W)),
    // CSR
    (exeFcn === CSR_RS) -> (csrRegFile(imm_i) + op1Data),
    (exeFcn === CSR_RC) -> (csrRegFile(imm_i) & (~op1Data)),
  ))
  io.dmem.addr := aluOut // LWやらSWやらするデータメモリのアドレス
  // wbするデータを指定．デフォはALUの結果を書き込み
  val wbData = MuxCase(aluOut, Seq(
    (readP === RS2_R) -> rs2Data,
    (readP === MEM_R) -> io.dmem.data,
    (readP === PC_R) -> pc,
  ))
  io.dmem.wData := wbData
    

  // MAステージ
  /* ******************************** */
  // SW命令なら書き込み信号などを設定
  io.dmem.wEn := false.B
  when(wbP === MEM_W){
    io.dmem.wEn := true.B // SWならメモリへの書き込みenableをtrue
  }
  // CSR系
  val csrOrg = csrRegFile(imm_i)
  when(wbP === CSR_W){
    csrRegFile(imm_i) := wbData
  }
  // ECALL
  when(wbP === ECL_W){
    printf("ECALL!\n")
    csrRegFile(0x342) := 11.U(WORD_LEN.W)
  }


  // WBステージ
  /* ******************************** */
  // 分岐命令系はpcに書き込み
  regFile(rdAddr) := MuxCase(wbData, Seq(
    (wbP === MEM_W) -> regFile(rdAddr), // メモリ書きだけならなにもせん 
    (wbP === PC_W) -> regFile(rdAddr), // 分岐系はなにもせん
    (wbP === JMP_W) -> pcPlus4, // ジャンプ命令はrdには今のpcを書き込む
    (wbP === CSR_W) -> csrOrg, // CSRはrdに元のCSR値を書き込む
  ))
  pc := MuxCase(pcPlus4, Seq(
    (wbP === PC_W) -> wbData, // 分岐命令系はpcに書き込み
    (wbP === JMP_W) -> wbData, // ジャンプ命令のpcはALUの計算値
    (wbP === ECL_W) -> csrRegFile(0x305),
  ))


  //**********************************
  // Debug
  //io.exit := (io.imem.inst === 0x00602823.U(WORD_LEN.W))
  io.exit := (pc === 0x44.U(WORD_LEN.W))
  //when(cnt > 10.U){
  printf("---------\n")
  printf(p"pc : 0x${Hexadecimal(pc)}\n")
  printf(p"inst : 0x${Hexadecimal(io.imem.inst)}\n")
  printf(p"rs1_addr : $rs1Addr\n")
  printf(p"rs2_addr : $rs2Addr\n")
  printf(p"rd_addr : $rdAddr\n")
  printf(p"rs1Data : 0x${Hexadecimal(rs1Data)}\n")
  printf(p"rs2Data : 0x${Hexadecimal(rs2Data)}\n")
  printf(p"aluOut : 0x${Hexadecimal(aluOut)}\n")
  printf(p"io.gp : ${regFile(3)}\n")
  printf("---------\n")
  //}

}