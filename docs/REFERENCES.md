# References and Credits

MLE stands on prior research and open data. This list credits the works that informed its taxonomy, rules, and evaluation. Where we describe or apply a finding, any misreading is ours, not the authors'. Authors or maintainers who want an entry corrected, expanded, or removed are invited to open an issue.

Bibliographic details were checked against the publishers' records (DOI, arXiv, ACL Anthology) on 2026-10-11. What each recent study reports, and how far MLE relies on it, is set out in [METHODOLOGY.md §F](METHODOLOGY.md). A citation explains why a category exists; the rules themselves are judged by the benchmark.

## Linguistics, Pragmatics, and Framing

| Work | Informs |
| :--- | :--- |
| Blom, J. N., & Hansen, K. R. (2015). [Click bait: Forward-reference as lure in online news headlines](https://doi.org/10.1016/j.pragma.2014.11.010). *Journal of Pragmatics*, 76, 87–100. | Withheld detail (cataphora, discourse deixis) |
| Scott, K. (2021). [You won't believe what's in this paper! Clickbait, relevance and the curiosity gap](https://doi.org/10.1016/j.pragma.2020.12.023). *Journal of Pragmatics*, 175, 53–66. | Withheld detail: definite reference with no content ("this city", "here's what they found") |
| Scott, K. (2023). ['Deceptive' clickbait headlines: Relevance, intentions, and lies](https://doi.org/10.1016/j.pragma.2023.10.004). *Journal of Pragmatics*, 218, 71–82. | Clickbait written in conventional headline form |
| Entman, R. M. (1993). [Framing: Toward Clarification of a Fractured Paradigm](https://doi.org/10.1111/j.1460-2466.1993.tb01304.x). *Journal of Communication*, 43(4). | Conflict framing |
| Lakoff, G., & Johnson, M. (1980). [Metaphors We Live By](https://press.uchicago.edu/ucp/books/book/chicago/M/bo3637992.html). University of Chicago Press. | Conflict and war metaphor |
| Halliday, M. A. K., & Matthiessen, C. M. I. M. (2014). *An Introduction to Functional Grammar* (4th ed.). Routledge. | Modality, evidentiality |
| Kovach, B., & Rosenstiel, T. (2014). *The Elements of Journalism* (3rd ed.). Crown. | Sourcing and verification norms |
| Otis, A. (2024). [The effects of transparency cues on news source credibility online: An investigation of 'opinion labels'](https://doi.org/10.1177/14648849221129001). *Journalism*, 25(1), 198–217. | Signed and labelled opinion (codebook rule 2) |

## Clickbait Detection and Tactic Taxonomies

| Work | Informs |
| :--- | :--- |
| Chakraborty, A., et al. (2016). [Stop Clickbait: Detecting and Preventing Clickbaits in Online News Media](https://arxiv.org/abs/1610.09786). ASONAM. ([dataset](https://github.com/bhargaviparanjape/clickbait)) | Features; legacy regression corpus |
| Potthast, M., Gollub, T., Komlossy, K., Schuster, S., Wiegmann, M., Garces Fernandez, E. P., Hagen, M., & Stein, B. (2018). [Crowdsourcing a Large Corpus of Clickbait on Twitter](https://aclanthology.org/C18-1127/). *COLING 2018*, 1498–1507. ([archive](https://webis.de/data/webis-clickbait-17.html)) | Background corpus |
| Nofar, L., Portal, T., Elbaz, A., Apartsin, A., & Aperstein, Y. (2025). [An Interpretable Benchmark for Clickbait Detection and Tactic Attribution](https://arxiv.org/abs/2509.10937). arXiv:2509.10937. | Tactic-level taxonomy (ambiguous references, direct appeals) |
| Jácobo-Morales, D., & Marino-Jiménez, M. (2024). [Clickbait: Research, challenges and opportunities – A systematic literature review](https://doi.org/10.30935/ojcmt/15267). *Online Journal of Communication and Media Technologies*, 14(4), e202458. | Survey of definitions and approaches |
| Pujahari, A., & Sisodia, D. S. (2021). [Clickbait detection using multiple categorisation techniques](https://doi.org/10.1177/0165551519871822). *Journal of Information Science*, 47(1), 118–128. | Hand-crafted surface features |
| Alarfaj, F. K., Muqadas, A., Khan, H. U., & Naz, A. (2025). [Clickbait detection in news headlines using RoBERTa-Large language model and deep embeddings](https://doi.org/10.1038/s41598-025-30229-5). *Scientific Reports*, 16, 691. | Model explanations point to wording, not topic |
| Kuntur, S., Ningrum, P. K., Wróblewska, A., Ganzha, M., & Paprzycki, M. (2026). [Clickbait detection: quick inference with maximum impact](https://arxiv.org/abs/2604.08148). arXiv:2604.08148. | Lightweight-inference motivation |
| Michaluk, W., Urban, T., Kubita, M., Kuntur, S., & Wróblewska, A. (2026). [Click it or Leave it: Detecting and Spoiling Clickbait with Informativeness Measures and Large Language Models](https://arxiv.org/abs/2602.18171). arXiv:2602.18171. | Interpretable cues: second person, superlatives |
| Broscoțeanu, D.-M., & Ionescu, R. T. (2023). [A Novel Contrastive Learning Method for Clickbait Detection on RoCliCo: A Romanian Clickbait Corpus of News Articles](https://aclanthology.org/2023.findings-emnlp.640/). *Findings of EMNLP 2023*, 9547–9555. | Comparing a headline with its own article (cf. the summary resolver) |

## Science Communication

| Work | Informs |
| :--- | :--- |
| Sumner, P., et al. (2014). [The association between exaggeration in health-related science news and academic press releases](https://doi.org/10.1136/bmj.g7015). *BMJ*, 349, g7015. | Early science |

## Evaluation Datasets

| Dataset | Reference |
| :--- | :--- |
| BABE | Spinde, T., et al. (2021). [Neural Media Bias Detection Using Distant Supervision With BABE](https://aclanthology.org/2021.findings-emnlp.101/). *Findings of EMNLP*. Used under its own license; cached only, never redistributed here. |
| CheckThat! 2024, Task 2 | Struß, J. M., et al. (2024). [Overview of the CLEF-2024 CheckThat! Lab Task 2 on Subjectivity in News Articles](https://doi.org/10.5281/zenodo.18525179). |
| Webis-Clickbait-22 | Hagen, M., et al. (2022). [Clickbait Spoiling via Question Answering and Passage Retrieval](https://arxiv.org/abs/2203.10282). ACL. Fröbe, M., et al. (2023). [SemEval-2023 Task 5: Clickbait Spoiling](https://aclanthology.org/2023.semeval-1.312/). |

## Related Tools Considered

* **VADER** (Hutto & Gilbert, 2014), [VADER: A Parsimonious Rule-based Model for Sentiment Analysis of Social Media Text](https://ojs.aaai.org/index.php/ICWSM/article/view/14550). We considered it and chose to analyze writing register rather than event sentiment (see METHODOLOGY §4).
