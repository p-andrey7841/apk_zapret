package com.zapret.droid.strategies

object StrategyPresets {

    val GENERAL = Strategy(
        id = "general",
        name = "General",
        description = "Fake QUIC + STUN. Основная стратегия для YouTube и Discord",
        dpiDesync = listOf(DesyncMethod.FAKE),
        dpiDesyncRepeats = 6,
        fakeData = FakeData.QUIC_GOOGLE,
        fakeTtl = 8,
        services = listOf(Service.YOUTUBE, Service.DISCORD, Service.GOOGLE)
    )

    val ALT1 = Strategy(
        id = "alt1",
        name = "ALT 1 — Multisplit seqovl=681",
        description = "TCP multisplit с sequence overlap. Хорошо работает для YouTube",
        dpiDesync = listOf(DesyncMethod.MULTISPLIT),
        splitPos = SplitPos.POS_1,
        seqovl = 681,
        seqovlPattern = SeqovlPattern.GOOGLE_TLS,
        dpiDesyncRepeats = 6,
        services = listOf(Service.YOUTUBE, Service.GOOGLE)
    )

    val ALT2 = Strategy(
        id = "alt2",
        name = "ALT 2 — Fake + Multisplit seqovl=652",
        description = "Комбо: fake-пакет + multisplit. Слоёная обфускация",
        dpiDesync = listOf(DesyncMethod.FAKE, DesyncMethod.MULTISPLIT),
        splitPos = SplitPos.POS_1,
        seqovl = 652,
        seqovlPattern = SeqovlPattern.GOOGLE_TLS,
        dpiDesyncRepeats = 6,
        fakeData = FakeData.QUIC_GOOGLE,
        services = listOf(Service.YOUTUBE, Service.DISCORD, Service.GOOGLE)
    )

    val ALT3 = Strategy(
        id = "alt3",
        name = "ALT 3 — Hostfakesplit + BadSeq",
        description = "Подмена host-заголовка + неверный sequence number",
        dpiDesync = listOf(DesyncMethod.HOSTFAKESPLIT),
        fooling = listOf(FoolingMethod.BADSEQ),
        badseqIncrement = 2,
        dpiDesyncRepeats = 6,
        services = listOf(Service.YOUTUBE, Service.DISCORD)
    )

    val ALT4 = Strategy(
        id = "alt4",
        name = "ALT 4 — Syndata + Multidisorder",
        description = "Данные в SYN-пакете + перемешивание порядка пакетов",
        dpiDesync = listOf(DesyncMethod.SYNDATA, DesyncMethod.MULTIDISORDER),
        dpiDesyncRepeats = 14,
        services = listOf(Service.YOUTUBE, Service.DISCORD, Service.GOOGLE)
    )

    val ALT5 = Strategy(
        id = "alt5",
        name = "ALT 5 — BadSeq increment=1000",
        description = "Большой сдвиг sequence number в fake-пакетах",
        dpiDesync = listOf(DesyncMethod.FAKE),
        fooling = listOf(FoolingMethod.BADSEQ),
        badseqIncrement = 1000,
        dpiDesyncRepeats = 8,
        fakeData = FakeData.TLS_GOOGLE,
        services = listOf(Service.YOUTUBE, Service.DISCORD, Service.GOOGLE)
    )

    val ALT6 = Strategy(
        id = "alt6",
        name = "ALT 6 — Fake + Fakedsplit seqovl=664",
        description = "Fake-пакет + fakedsplit для TCP-портов. seqovl=664",
        dpiDesync = listOf(DesyncMethod.FAKE, DesyncMethod.FAKEDSPLIT),
        splitPos = SplitPos.POS_1,
        seqovl = 664,
        seqovlPattern = SeqovlPattern.GOOGLE_TLS,
        dpiDesyncRepeats = 6,
        fakeData = FakeData.QUIC_GOOGLE,
        services = listOf(Service.YOUTUBE, Service.DISCORD, Service.GOOGLE)
    )

    val ALT7 = Strategy(
        id = "alt7",
        name = "ALT 7 — Multisplit seqovl=568 + TS fooling",
        description = "Multisplit с меньшим seqovl + подмена TCP timestamp",
        dpiDesync = listOf(DesyncMethod.MULTISPLIT),
        splitPos = SplitPos.POS_1,
        seqovl = 568,
        seqovlPattern = SeqovlPattern.GOOGLE_TLS,
        fooling = listOf(FoolingMethod.TS),
        dpiDesyncRepeats = 6,
        services = listOf(Service.YOUTUBE, Service.GOOGLE)
    )

    val ALT8 = Strategy(
        id = "alt8",
        name = "ALT 8 — Fake seqovl=679 Discord",
        description = "Оптимизировано для Discord media-серверов. seqovl=679",
        dpiDesync = listOf(DesyncMethod.FAKE, DesyncMethod.MULTISPLIT),
        splitPos = SplitPos.POS_1,
        seqovl = 679,
        seqovlPattern = SeqovlPattern.GOOGLE_TLS,
        dpiDesyncRepeats = 6,
        fakeData = FakeData.QUIC_DBANK,
        filterL7 = listOf(L7Protocol.DISCORD, L7Protocol.STUN),
        services = listOf(Service.DISCORD)
    )

    val ALT9 = Strategy(
        id = "alt9",
        name = "ALT 9 — Any-protocol + cutoff=n2",
        description = "Протокол-агностик режим. Работает для игр и нестандартных протоколов",
        dpiDesync = listOf(DesyncMethod.FAKE),
        anyProtocol = true,
        desyncCutoff = DesyncCutoff.N2,
        dpiDesyncRepeats = 12,
        fakeData = FakeData.UNKNOWN_UDP,
        services = listOf(Service.GAMING, Service.DISCORD)
    )

    val ALT10 = Strategy(
        id = "alt10",
        name = "ALT 10 — Multisplit midsld pos",
        description = "Разрезает TLS ClientHello в середине SNI-домена",
        dpiDesync = listOf(DesyncMethod.MULTISPLIT),
        splitPos = SplitPos.MIDSLD,
        seqovl = 681,
        seqovlPattern = SeqovlPattern.GOOGLE_TLS,
        dpiDesyncRepeats = 6,
        services = listOf(Service.YOUTUBE, Service.GOOGLE)
    )

    val ALT11 = Strategy(
        id = "alt11",
        name = "ALT 11 — Fake TLS MAX.RU",
        description = "Fake-пакет на основе TLS-хендшейка max.ru",
        dpiDesync = listOf(DesyncMethod.FAKE),
        fakeData = FakeData.TLS_MAX,
        dpiDesyncRepeats = 6,
        services = listOf(Service.YOUTUBE, Service.DISCORD, Service.GOOGLE)
    )

    val ALT12 = Strategy(
        id = "alt12",
        name = "ALT 12 — Fake + Multisplit sniext+1",
        description = "Разрез после SNI Extension + fake-пакет",
        dpiDesync = listOf(DesyncMethod.FAKE, DesyncMethod.MULTISPLIT),
        splitPos = SplitPos.SNIEXT_PLUS_1,
        seqovl = 681,
        seqovlPattern = SeqovlPattern.GOOGLE_TLS,
        dpiDesyncRepeats = 6,
        fakeData = FakeData.QUIC_GOOGLE,
        services = listOf(Service.YOUTUBE, Service.DISCORD, Service.GOOGLE)
    )

    val FAKE_TLS_AUTO = Strategy(
        id = "fake_tls_auto",
        name = "Fake TLS AUTO",
        description = "Автоматически генерирует рандомный TLS ClientHello. Эффективен против pattern-DPI",
        dpiDesync = listOf(DesyncMethod.FAKE),
        fakeTlsMod = FakeTlsMod.RND,
        fakeTlsSni = "www.google.com",
        dpiDesyncRepeats = 6,
        services = listOf(Service.YOUTUBE, Service.DISCORD, Service.GOOGLE)
    )

    val FAKE_TLS_AUTO_DUPSID = Strategy(
        id = "fake_tls_auto_dupsid",
        name = "Fake TLS AUTO (DupSID)",
        description = "Дублирование Session ID в fake-пакете",
        dpiDesync = listOf(DesyncMethod.FAKE),
        fakeTlsMod = FakeTlsMod.DUPSID,
        fakeTlsSni = "www.google.com",
        dpiDesyncRepeats = 6,
        services = listOf(Service.YOUTUBE, Service.DISCORD, Service.GOOGLE)
    )

    val SIMPLE_FAKE = Strategy(
        id = "simple_fake",
        name = "Simple Fake",
        description = "Упрощённый fake без сплиттинга. Попробуй если другие не работают",
        dpiDesync = listOf(DesyncMethod.FAKE),
        fakeData = FakeData.QUIC_GOOGLE,
        dpiDesyncRepeats = 6,
        fooling = emptyList(),
        services = listOf(Service.YOUTUBE, Service.DISCORD, Service.GOOGLE)
    )

    val SIMPLE_FAKE_UDP = Strategy(
        id = "simple_fake_udp",
        name = "Simple Fake UDP",
        description = "Fake-пакет только для UDP/QUIC трафика",
        dpiDesync = listOf(DesyncMethod.FAKE),
        fakeData = FakeData.QUIC_GOOGLE,
        dpiDesyncRepeats = 6,
        udpPorts = listOf(443, 19294, 19295, 19302, 19303),
        services = listOf(Service.DISCORD, Service.GAMING)
    )

    val GOOGLE_IP_ZERO = Strategy(
        id = "google_ip_zero",
        name = "Google IP-ID Zero",
        description = "Обнуляет IP ID для обхода Google/YouTube IP-фингерпринтинга",
        dpiDesync = listOf(DesyncMethod.MULTISPLIT),
        splitPos = SplitPos.POS_1,
        seqovl = 681,
        seqovlPattern = SeqovlPattern.GOOGLE_TLS,
        ipIdZero = true,
        dpiDesyncRepeats = 6,
        services = listOf(Service.YOUTUBE, Service.GOOGLE)
    )

    val ALL: List<Strategy> = listOf(
        GENERAL,
        ALT1, ALT2, ALT3, ALT4, ALT5,
        ALT6, ALT7, ALT8, ALT9, ALT10,
        ALT11, ALT12,
        FAKE_TLS_AUTO, FAKE_TLS_AUTO_DUPSID,
        SIMPLE_FAKE, SIMPLE_FAKE_UDP,
        GOOGLE_IP_ZERO
    )
}
