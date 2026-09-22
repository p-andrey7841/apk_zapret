package com.zapret.droid.strategies

data class Strategy(
    val id: String,
    val name: String,
    val description: String,
    val tcpPorts: List<Int> = listOf(80, 443, 2053, 2083, 2087, 2096, 8443),
    val udpPorts: List<Int> = listOf(443),
    val dpiDesync: List<DesyncMethod> = listOf(DesyncMethod.FAKE),
    val dpiDesyncRepeats: Int = 6,
    val splitPos: SplitPos = SplitPos.POS_1,
    val seqovl: Int = 0,
    val seqovlPattern: SeqovlPattern = SeqovlPattern.GOOGLE_TLS,
    val fooling: List<FoolingMethod> = emptyList(),
    val badseqIncrement: Int = 2,
    val fakeTtl: Int = 8,
    val fakeData: FakeData = FakeData.QUIC_GOOGLE,
    val fakeTlsMod: FakeTlsMod = FakeTlsMod.NONE,
    val fakeTlsSni: String = "www.google.com",
    val anyProtocol: Boolean = false,
    val desyncCutoff: DesyncCutoff = DesyncCutoff.N4,
    val ipIdZero: Boolean = false,
    val filterL7: List<L7Protocol> = emptyList(),
    val services: List<Service> = Service.entries,
    val telegramEnabled: Boolean = true
)

enum class DesyncMethod { FAKE, MULTISPLIT, FAKEDSPLIT, SYNDATA, MULTIDISORDER, HOSTFAKESPLIT }
enum class SplitPos { POS_1, POS_2, MIDSLD, SNIEXT_PLUS_1 }
enum class SeqovlPattern { GOOGLE_TLS, MAX_TLS, PDA_TLS, NONE }
enum class FoolingMethod { BADSEQ, TS, MD5SIG }
enum class FakeData { QUIC_GOOGLE, QUIC_DBANK, TLS_GOOGLE, TLS_MAX, TLS_4PDA, STUN, UNKNOWN_UDP }
enum class FakeTlsMod { NONE, RND, DUPSID }
enum class DesyncCutoff { N2, N3, N4, N5 }
enum class L7Protocol { DISCORD, STUN }
enum class Service { YOUTUBE, DISCORD, TELEGRAM, GOOGLE, GAMING }
