# CBSE Class XII English Core (301) — Project Work

**Topic:** From Grand Central to the Silver Screen: An Intertextual Film Review Analysing
Parallels Between Jack Finney's *The Third Level* and Modern Cinematic Portrayals of
Psychological Refuge

**Film selected for review:** *Midnight in Paris* (dir. Woody Allen, 2011)

## Files

| File | What it is |
|---|---|
| `Class-XII-English-Project-The-Third-Level.docx` | The editable project. Fill in your name, class, roll number and session, then print. |
| `Class-XII-English-Project-The-Third-Level.pdf` | Print-ready version, 35 pages, A4. |
| `plates/` | The six original plates as PNGs, in case any need reprinting separately. |
| `source/` | The scripts that generate the document and the plates. |

## Before you print

The cover page, certificate and acknowledgement carry **dotted fill-in lines** rather than
invented details. Complete these first:

- Cover page — Name, Class & Section, Roll Number, Session
- Certificate — candidate name and session
- Signature lines are left blank for the subject teacher, the Principal and the candidate

Print single-sided on A4 and bind in a hard-bound lace file, as the brief requires. The left
margin is set to 3 cm so that binding does not eat into the text.

## Structure

| Pages | Contents |
|---|---|
| i–v | Cover, title page, certificate, acknowledgement, declaration |
| 1–2 | Contents; statement of purpose, objectives and method |
| 3–9 | **Part A — Textual Foundation** (summary, three key quotes, escapism analysis, themes) |
| 10–19 | **Part B — Film Review** (basic info, plot, theme connection, character parallel, critical comment, personal response) |
| 20–22 | **Part C — Creative Extension** (poster design + designer's note) |
| 23–25 | **Part D — Conclusion** (final verdict) |
| 26–30 | Knowledge gained, viva preparation, glossary, bibliography |

## Notes on how it was built

- **Every quotation from *The Third Level* was checked word by word against the prescribed
  NCERT text** rather than taken from summaries or study sites. Dates, addresses and figures
  (11 June 1894, 18 July 1894, 941 Willard Street, eight hundred dollars) are as printed.
- Paul's "Golden Age thinking" speech from *Midnight in Paris* was verified against published
  sources. Where other scenes are referred to, they are described rather than quoted, so that
  nothing is attributed to the film that cannot be checked.
- **All six plates are original designs.** No photograph, film still or promotional artwork is
  reproduced anywhere in the file.
- The palette is restricted throughout to **blue, black, brown, purple and sky blue**, the
  colours permitted by the project guidelines.

## One thing to be aware of

The school brief specifies **15–20 pages and 1600–2000 words**. This file is **35 pages** as
requested, which means Parts A–D run to roughly **4,000 words** — about double the stated word
limit. The page count is made up honestly (six full-page plates, section dividers, appendices)
rather than by padding, but the word count does exceed the brief.

If the word limit matters more than the page count, the prose can be cut to ~2,000 words, which
would bring the file to roughly 20–24 pages and back inside the guidelines.

## Rebuilding

```sh
cd source
npm install docx
./render.sh fig1 1600 900      # and fig2 1600 1100, fig3 1600 960,
                               # fig4 1600 1000, fig5 1600 900, poster 1200 1800
node build.js ../project.docx
soffice --headless --convert-to pdf project.docx
```
