package com.medialiteracy.benchmark

enum class SourceKind { TONE_BABE, TONE_CHECKTHAT, TEASE_WEBIS22 }

/** A published research dataset, pinned to an exact file by checksum. Cached in fixtures/external/, never committed. */
data class ExternalSource(
    val id: String,
    val kind: SourceKind,
    val url: String,
    val sha256: String,
    val fileName: String,
    val licence: String,
    val year: Int,
    val citation: String,
)

val EXTERNAL_SOURCES = listOf(
    ExternalSource(
        id = "babe",
        kind = SourceKind.TONE_BABE,
        url = "https://raw.githubusercontent.com/Media-Bias-Group/Neural-Media-Bias-Detection-Using-Distant-Supervision-With-BABE/fb79f646dcabf1746f5ebedc06ed8dd61ba78188/data/final_labels_SG2.csv",
        sha256 = "c02ebe82a8411f15e9dc63afed27826d61b84df56b5e1511f0f40bf8a688aeaf",
        fileName = "babe_final_labels_SG2.csv",
        licence = "AGPL-3.0 (cached only; never committed or copied into the rule pack)",
        year = 2021,
        citation = "Spinde et al., Neural Media Bias Detection Using Distant Supervision With BABE, Findings of EMNLP 2021, https://aclanthology.org/2021.findings-emnlp.101/",
    ),
    ExternalSource(
        id = "checkthat-2024-subj",
        kind = SourceKind.TONE_CHECKTHAT,
        url = "https://gitlab.com/checkthat_lab/clef2024-checkthat-lab/-/raw/3508f4a847b4471135e407c6ec73f9aef39f450f/task2/data/subtask-2-english/test_en_gold.tsv",
        sha256 = "c25912bfd1263e6adfe7fdfff822e97e8c794cae5ccee0dd57b0c0cae9e08150",
        fileName = "checkthat2024_task2_test_en_gold.tsv",
        licence = "CC-BY-4.0",
        year = 2024,
        citation = "Struß et al., Overview of the CLEF-2024 CheckThat! Lab Task 2 on Subjectivity in News Articles, https://doi.org/10.5281/zenodo.18525179",
    ),
    ExternalSource(
        id = "webis-clickbait-22",
        kind = SourceKind.TEASE_WEBIS22,
        url = "https://zenodo.org/api/records/8136637/files/webis-clickbait-22.zip/content",
        sha256 = "30acf935efc82e4f99d45943827c4e14551d1554bc43514c90e4948c1514586b",
        fileName = "webis-clickbait-22.zip",
        licence = "CC-BY-4.0",
        year = 2022,
        citation = "Hagen et al., Clickbait Spoiling via Question Answering and Passage Retrieval, ACL 2022, https://arxiv.org/abs/2203.10282; Fröbe et al., SemEval-2023 Task 5: Clickbait Spoiling, https://aclanthology.org/2023.semeval-1.312/",
    ),
)

