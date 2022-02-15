package pipeline

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

  // パイプラインレジスタ
  /* ******************************** */
  // PC，inst系
  // IF -> ID
  val idPcReg = RegInit(0.U(WORD_LEN.W))
  val idPcPlus4Reg = RegInit(0.U(WORD_LEN.W))
  val idInstReg = RegInit(0.U(WORD_LEN.W))
  // ID -> EX
  val exPcReg = RegInit(0.U(WORD_LEN.W))
  val exPcPlus4Reg = RegInit(0.U(WORD_LEN.W))
  val exInstReg = RegInit(0.U(WORD_LEN.W))
  // EX -> MA
  val maPcReg = RegInit(0.U(WORD_LEN.W))
  val maPcPlus4Reg = RegInit(0.U(WORD_LEN.W))
  val maInstReg = RegInit(0.U(WORD_LEN.W))
  // MA -> WB
  val wbPcReg = RegInit(0.U(WORD_LEN.W))
  val wbPcPlus4Reg = RegInit(0.U(WORD_LEN.W))
  val wbInstReg = RegInit(0.U(WORD_LEN.W))

  // 各種データ系
  val exRs1Data = RegInit(0.U(WORD_LEN.W)) 
  val maRs1Data = RegInit(0.U(WORD_LEN.W)) 
  val wbRs1Data = RegInit(0.U(WORD_LEN.W)) 
  val exRs2Data = RegInit(0.U(WORD_LEN.W)) 
  val maRs2Data = RegInit(0.U(WORD_LEN.W)) 
  val wbRs2Data = RegInit(0.U(WORD_LEN.W)) 
  val exRdAddr = RegInit(0.U(WORD_LEN.W))
  val maRdAddr = RegInit(0.U(WORD_LEN.W))
  val wbRdAddr = RegInit(0.U(WORD_LEN.W))
  val maDmemAddr = RegInit(0.U(WORD_LEN.W))
  val wbDmemAddr = RegInit(0.U(WORD_LEN.W))
  val maWbData = RegInit(0.U(WORD_LEN.W))
  val wbWbData = RegInit(0.U(WORD_LEN.W))
  val wbCsrOrg = RegInit(0.U(WORD_LEN.W))
  // 関数・引数系
  val exExeFcn = RegInit(0.U(EXE_FUN_LEN.W))
  val maExeFcn = RegInit(0.U(EXE_FUN_LEN.W))
  val wbExeFcn = RegInit(0.U(EXE_FUN_LEN.W))
  val exOp1Data = RegInit(0.U(WORD_LEN.W))
  val maOp1Data = RegInit(0.U(WORD_LEN.W))
  val wbOp1Data = RegInit(0.U(WORD_LEN.W))
  val exOp2Data = RegInit(0.U(WORD_LEN.W))
  val maOp2Data = RegInit(0.U(WORD_LEN.W))
  val wbOp2Data = RegInit(0.U(WORD_LEN.W))
  val exReadP = RegInit(0.U(RP_LEN.W))
  val maReadP = RegInit(0.U(RP_LEN.W))
  val wbReadP = RegInit(0.U(RP_LEN.W))
  val exWbP = RegInit(0.U(WP_LEN.W))
  val maWbP = RegInit(0.U(WP_LEN.W))
  val wbWbP = RegInit(0.U(WP_LEN.W))
  // デコード値系
  val exImm_i = RegInit(0.U(WORD_LEN.W))
  val maImm_i = RegInit(0.U(WORD_LEN.W))
  val wbImm_i = RegInit(0.U(WORD_LEN.W))
  val exImm_i_sext = RegInit(0.U(WORD_LEN.W))
  val maImm_i_sext = RegInit(0.U(WORD_LEN.W))
  val wbImm_i_sext = RegInit(0.U(WORD_LEN.W))
  val exImm_z = RegInit(0.U(WORD_LEN.W))
  val maImm_z = RegInit(0.U(WORD_LEN.W))
  val wbImm_z = RegInit(0.U(WORD_LEN.W))
  val exImm_z_uext = RegInit(0.U(WORD_LEN.W))
  val maImm_z_uext = RegInit(0.U(WORD_LEN.W))
  val wbImm_z_uext = RegInit(0.U(WORD_LEN.W))
  val exImm_b = RegInit(0.U(WORD_LEN.W))
  val maImm_b = RegInit(0.U(WORD_LEN.W))
  val wbImm_b = RegInit(0.U(WORD_LEN.W))
  val exImm_b_sext = RegInit(0.U(WORD_LEN.W))
  val maImm_b_sext = RegInit(0.U(WORD_LEN.W))
  val wbImm_b_sext = RegInit(0.U(WORD_LEN.W))
  val exImm_j = RegInit(0.U(WORD_LEN.W))
  val maImm_j = RegInit(0.U(WORD_LEN.W))
  val wbImm_j = RegInit(0.U(WORD_LEN.W))
  val exImm_j_sext = RegInit(0.U(WORD_LEN.W))
  val maImm_j_sext = RegInit(0.U(WORD_LEN.W))
  val wbImm_j_sext = RegInit(0.U(WORD_LEN.W))
  val exImm_u = RegInit(0.U(WORD_LEN.W))
  val maImm_u = RegInit(0.U(WORD_LEN.W))
  val wbImm_u = RegInit(0.U(WORD_LEN.W))
  val exImm_u_sext = RegInit(0.U(WORD_LEN.W))
  val maImm_u_sext = RegInit(0.U(WORD_LEN.W))
  val wbImm_u_sext = RegInit(0.U(WORD_LEN.W))
  /* ******************************** */


  // IFステージ
  /* ******************************** */
  val pc = RegInit(0.U(WORD_LEN.W))
  // pcを4ずつカウントアップ
  val pcPlus4 = pc + 4.U(WORD_LEN.W)
  pc := pc + 4.U(WORD_LEN.W)
  // pcの値のアドレスにある命令をフェッチしてくる
  io.imem.addr := pc

  // デバッグ用のサイクルカウント
  val cnt = RegInit(0.U(WORD_LEN.W))
  cnt := cnt + 1.U(WORD_LEN.W)

  // IF -> ID
  idPcReg := pc
  idPcPlus4Reg := pcPlus4
  idInstReg := io.imem.inst


  // IDステージ
  /* ******************************** */
  // 配線としてinstから各要素を抜き出す
  val rs1Addr = idInstReg(19, 15)
  val rs2Addr = idInstReg(24, 20)
  val rdAddr = idInstReg(11, 7)
  // アドレスが非ゼロならレジスタから読み取り，ゼロなら何もせん
  val rs1Data = Mux(rs1Addr =/= 0.U(WORD_LEN.W), regFile(rs1Addr), 0.U(WORD_LEN.W))
  val rs2Data = Mux(rs2Addr =/= 0.U(WORD_LEN.W), regFile(rs2Addr), 0.U(WORD_LEN.W))
  // I形式(LWとか)の即値を抜き出す
  val imm_i = idInstReg(31, 20)
  val imm_z = idInstReg(19, 15)
  // 32bitのうちimm_iの先頭bitを20個拡張して，下12個はimm_i
  val imm_i_sext = Cat(Fill(20, imm_i(11)), imm_i)
  val imm_z_uext = Cat(0.U(27.W), imm_z)
  // S形式(SWとか)の即値を抜き出す
  val imm_s = Cat(idInstReg(31, 25), idInstReg(11, 7))
  // 32bitのうちimm_sの先頭bitを20個拡張して，下12個はimm_i
  val imm_s_sext = Cat(Fill(20, imm_s(11)), imm_s)
  // B形式の即値
  val imm_b = Cat(idInstReg(31), idInstReg(7), idInstReg(30, 25), idInstReg(11, 8))
  val imm_b_sext = Cat(Fill(19, imm_b(11)), imm_b, 0.U(1.W))
  // J形式
  val imm_j = Cat(idInstReg(31), idInstReg(19, 12), idInstReg(20), idInstReg(30, 21))
  val imm_j_sext = Cat(Fill(11, imm_j(19)), imm_j, 0.U(1.W))
  // U形式
  val imm_u = idInstReg(31, 12)
  val imm_u_sext = imm_u << 12

  val cSignals = ListLookup(idInstReg,
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
      JAL -> List(ALU_ADD, idPcReg, imm_j_sext, ALU_R, JMP_W),
      JALR -> List(ALU_JALR, rs1Data, imm_i_sext, ALU_R, JMP_W), // imm_"i" に注意
      // 即値ロード
      LUI -> List(ALU_ADD, 0.U(WORD_LEN.W), imm_u_sext, ALU_R, RD_W),
      AUIPC -> List(ALU_ADD, idPcReg, imm_u_sext, ALU_R, RD_W),
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

  // ID -> EX
  exPcReg := idPcReg
  exPcPlus4Reg := idPcPlus4Reg
  exInstReg := idInstReg
  // 各種データ系
  exRs1Data := rs1Data
  exRs2Data := rs2Data
  exRdAddr := rdAddr
  // 関数・引数系
  exExeFcn := exeFcn
  exOp1Data := op1Data
  exOp2Data := op2Data
  exReadP := readP
  exWbP := wbP
  // デコード値系
  exImm_i := imm_i
  exImm_i_sext := imm_i_sext
  exImm_z := imm_z
  exImm_z_uext := imm_z_uext
  exImm_b := imm_b
  exImm_b_sext := imm_b_sext
  exImm_j := imm_j
  exImm_j_sext := imm_j_sext
  exImm_u := imm_u
  exImm_u_sext := imm_u_sext


  // EXステージ
  /* ******************************** */
  // デフォルトはrdの元のレジスタ値
  val aluOut = MuxCase(regFile(rdAddr), Seq(
    (exExeFcn === ALU_ADD) -> (exOp1Data + exOp2Data),
    (exExeFcn === ALU_SUB) -> (exOp1Data - exOp2Data),
    (exExeFcn === ALU_AND) -> (exOp1Data & exOp2Data),
    (exExeFcn === ALU_OR) -> (exOp1Data | exOp2Data),
    (exExeFcn === ALU_XOR) -> (exOp1Data ^ exOp2Data),
    // なんかココらへんのUIntとかの変換が闇
    (exExeFcn === ALU_SLL) -> (exOp1Data << exOp2Data(4, 0)),
    (exExeFcn === ALU_SRL) -> (exOp1Data.asUInt() >> exOp2Data(4, 0)),
    (exExeFcn === ALU_SRA) -> (exOp1Data.asSInt() >> exOp2Data(4, 0)).asUInt(),
    (exExeFcn === ALU_SLT) -> (exOp1Data.asSInt() < exOp2Data.asSInt()),
    (exExeFcn === ALU_SLTU) -> (exOp1Data.asUInt() < exOp2Data.asUInt()),
    // ifがtrueなら左に分岐，falseなら右に分岐
    (exExeFcn === BR_BEQ) -> Mux(exOp1Data === exOp2Data, exPcReg + exImm_b_sext, exPcPlus4Reg),
    (exExeFcn === BR_BNE) -> Mux(exOp1Data =/= exOp2Data, exPcReg + exImm_b_sext, exPcPlus4Reg),
    (exExeFcn === BR_BLT) -> Mux(exOp1Data.asSInt() < exOp2Data.asSInt(), exPcReg + exImm_b_sext, exPcPlus4Reg),
    (exExeFcn === BR_BGE) -> Mux(exOp1Data.asSInt() >= exOp2Data.asSInt(), exPcReg + exImm_b_sext, exPcPlus4Reg),
    (exExeFcn === BR_BLTU) -> Mux(exOp1Data.asUInt() < exOp2Data.asUInt(), exPcReg + exImm_b_sext, exPcPlus4Reg),
    (exExeFcn === BR_BGEU) -> Mux(exOp1Data.asUInt() >= exOp2Data.asUInt(), exPcReg + exImm_b_sext, exPcPlus4Reg),
    (exExeFcn === ALU_JALR) -> ((exOp1Data + exOp2Data) & ~1.U(WORD_LEN.W)),
    // CSR
    (exExeFcn === CSR_RS) -> (csrRegFile(exImm_i) + exOp1Data),
    (exExeFcn === CSR_RC) -> (csrRegFile(exImm_i) & (~exOp1Data)),
  ))
  io.dmem.addr := aluOut // LWやらSWやらするデータメモリのアドレス
  // wbするデータを指定．デフォはALUの結果を書き込み
  val wbData = MuxCase(aluOut, Seq(
    (readP === RS2_R) -> exRs2Data,
    (readP === MEM_R) -> io.dmem.data,
    (readP === PC_R) -> exPcReg,
  ))
  //io.dmem.wData := wbData

  // ジャンプや分岐なら次サイクルのIDの命令と今サイクルの各データをバブルに上書き
  when((exWbP === PC_W) || (exWbP === JMP_W)){
    idInstReg := BUBBLE // 次のID命令にバブルを入れる
    // 次のExに入るデータを [ADDI x0 x0 0] のバブル化
    exExeFcn := ALU_ADD
    exOp1Data := regFile(0)
    exOp2Data := 0.U(WORD_LEN.W)
    exReadP := ALU_R
    exWbP := RD_W
    exRdAddr := 0.U(WORD_LEN.W)
    // ジャンプや分岐ならPCの更新
    pc := wbData // 分岐・ジャンプ命令系はpcに書き込み
    printf(p"<<<<<<<< PC change!: 0x${Hexadecimal(wbData)} >>>>>>>>\n")
  }

  when(exWbP === ECL_W){
    pc := csrRegFile(0x305)
    printf("vvvvvvvv ECALL! vvvvvvvv\n")
  }

  // EX -> MA
  maPcReg := exPcReg
  maPcPlus4Reg := exPcPlus4Reg
  maInstReg := exInstReg
  // 各種データ系
  maRs1Data := exRs1Data
  maRs2Data := exRs2Data
  maRdAddr := exRdAddr
  maDmemAddr := aluOut
  maWbData := wbData
  // 関数・引数系
  maExeFcn := exExeFcn
  maOp1Data := exOp1Data
  maOp2Data := exOp2Data
  maReadP := exReadP
  maWbP := exWbP
  // デコード値系
  maImm_i := exImm_i
  maImm_i_sext := exImm_i_sext
  maImm_z := exImm_z
  maImm_z_uext := exImm_z_uext
  maImm_b := exImm_b
  maImm_b_sext := exImm_b_sext
  maImm_j := exImm_j
  maImm_j_sext := exImm_j_sext
  maImm_u := exImm_u
  maImm_u_sext := exImm_u_sext
    

  // MAステージ
  /* ******************************** */
  // SW命令なら書き込み信号などを設定
  io.dmem.wEn := false.B
  io.dmem.wData := maWbData // 書き込みデータの更新
  io.dmem.addr := maDmemAddr
  when(maWbP === MEM_W){
    io.dmem.wEn := true.B // SWならメモリへの書き込みenableをtrue
  }
  // CSR系
  val csrOrg = csrRegFile(maImm_i)
  when(maWbP === CSR_W){
    csrRegFile(maImm_i) := maWbData
  }
  // ECALL
  when(maWbP === ECL_W){
    printf("ECALL!\n")
    csrRegFile(0x342) := 11.U(WORD_LEN.W)
  }

  // MA -> WB
  wbPcReg := maPcReg
  wbPcPlus4Reg := maPcPlus4Reg
  wbInstReg := maInstReg
  // 各種データ系
  wbRs1Data := maRs1Data
  wbRs2Data := maRs2Data
  wbRdAddr := maRdAddr
  wbDmemAddr := maDmemAddr
  wbWbData := maWbData
  wbCsrOrg := csrOrg
  // 関数・引数系
  wbExeFcn := maExeFcn
  wbOp1Data := maOp1Data
  wbOp2Data := maOp2Data
  wbReadP := maReadP
  wbWbP := maWbP
  // デコード値系
  wbImm_i := maImm_i
  wbImm_i_sext := maImm_i_sext
  wbImm_z := maImm_z
  wbImm_z_uext := maImm_z_uext
  wbImm_b := maImm_b
  wbImm_b_sext := maImm_b_sext
  wbImm_j := maImm_j
  wbImm_j_sext := maImm_j_sext
  wbImm_u := maImm_u
  wbImm_u_sext := maImm_u_sext


  // WBステージ
  /* ******************************** */
  // 分岐命令系はpcに書き込み
  regFile(wbRdAddr) := MuxCase(wbWbData, Seq(
    (wbWbP === MEM_W) -> regFile(wbRdAddr), // メモリ書きだけならなにもせん 
    (wbWbP === PC_W) -> regFile(wbRdAddr), // 分岐系はなにもせん
    (wbWbP === JMP_W) -> wbPcPlus4Reg, // ジャンプ命令はrdには今のpcを書き込む
    (wbWbP === CSR_W) -> wbCsrOrg, // CSRはrdに元のCSR値を書き込む
  ))
  //pc := MuxCase(wbPcPlus4Reg, Seq(
  //  (wbWbP === PC_W) -> wbWbData, // 分岐命令系はpcに書き込み
  //  (wbWbP === JMP_W) -> wbWbData, // ジャンプ命令のpcはALUの計算値
  //  (wbWbP === ECL_W) -> csrRegFile(0x305),
  //))


  //**********************************
  // Debug
  //io.exit := (io.imem.inst === 0x00602823.U(WORD_LEN.W))
  //io.exit := (pc === 0x44.U(WORD_LEN.W))
  // val UNIMP = "x_c0001073".U(WORD_LEN.W)
  io.exit := (io.imem.inst === UNIMP) // ctest用
  when(cnt > 50.U){
    printf("======== cnt over! ========\n")
    io.exit := true.B
  }
  //when(cnt > 10.U){
  printf("---------\n")
  printf(p"pc : 0x${Hexadecimal(pc)}\n")
  printf(p"  inst : 0x${Hexadecimal(io.imem.inst)}\n")
  printf(p"idPcReg: 0x${Hexadecimal(idPcReg)}\n")
  printf(p"  idInstReg : 0x${Hexadecimal(idInstReg)}\n")
  printf(p"  rs1Data : 0x${Hexadecimal(rs1Data)}\n")
  printf(p"  rs2Data : 0x${Hexadecimal(rs2Data)}\n")
  printf(p"exPcReg: 0x${Hexadecimal(exPcReg)}\n")
  printf(p"  exOp1Data : 0x${Hexadecimal(exOp1Data)}\n")
  printf(p"  exOp2Data : 0x${Hexadecimal(exOp2Data)}\n")
  printf(p"  aluOut : 0x${Hexadecimal(aluOut)}\n")
  printf(p"maPcReg: 0x${Hexadecimal(maPcReg)}\n")
  printf(p"  io.dmem.wData: 0x${Hexadecimal(io.dmem.wData)}\n")
  printf(p"wbPcReg: 0x${Hexadecimal(wbPcReg)}\n")
  printf(p"  regFile(${wbRdAddr}): 0x${Hexadecimal(regFile(wbRdAddr))}\n")
  printf(p"io.gp : ${regFile(3)}\n")
  printf("---------\n")
  printf("\n")
  //}

}