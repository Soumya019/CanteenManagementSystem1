const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, ShadingType, AlignmentType, BorderStyle, PageBreak, Footer,
  PageNumber, VerticalAlign, TabStopType, LeaderType, Tab,
} = require('docx');

const IMG = path.join(__dirname, 'img');

// ---------------------------------------------------------------- palette
const NAVY = '16314F', BLUE = '234E77', BROWN = '6B4423', PURPLE = '4B3B68';
const INK = '101820', SKY = '7FA8C7', MUTED = '5C6773';
const CREAM = 'F6F1E7', PALEBLUE = 'E4EDF5', PALEBROWN = 'F1E6D6', PALEPURPLE = 'EAE5F1';
const BODY = 'Georgia';

const CONTENT_W = 9071;           // A4 minus the binding margins set below
const NONE = { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' };
const NOBORDERS = { top: NONE, bottom: NONE, left: NONE, right: NONE };

// ---------------------------------------------------------------- helpers
const t = (text, o = {}) => new TextRun({ text, font: BODY, ...o });

const P = (text, o = {}) => new Paragraph({
  alignment: o.align || AlignmentType.JUSTIFIED,
  spacing: { before: o.before ?? 0, after: o.after ?? 160, line: o.line ?? 300 },
  indent: o.indent,
  children: Array.isArray(text) ? text : [t(text, { size: o.size || 22, color: o.color || INK, italics: o.italics, bold: o.bold })],
});

const LEAD = (text) => P([t(text, { size: 22, color: INK })], { after: 160 });

const H1 = (text, o = {}) => new Paragraph({
  spacing: { before: o.before ?? 0, after: 90 },
  children: [t(text, { size: 34, bold: true, color: NAVY })],
});

const RULE = (color = NAVY, size = 12) => new Paragraph({
  spacing: { before: 0, after: 220 },
  border: { bottom: { style: BorderStyle.SINGLE, size, color } },
  children: [t('', { size: 2 })],
});

const H2 = (text, o = {}) => new Paragraph({
  spacing: { before: o.before ?? 300, after: 120 },
  children: [t(text, { size: 25, bold: true, color: BROWN })],
});

const KICKER = (text) => new Paragraph({
  spacing: { before: 0, after: 60 },
  children: [t(text, { size: 17, color: PURPLE, characterSpacing: 60 })],
});

const CAPTION = (text) => new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 140, after: 0 },
  children: [t(text, { size: 18, italics: true, color: MUTED })],
});

const SPACER = (h = 200) => new Paragraph({ spacing: { after: h }, children: [t('', { size: 2 })] });

const BREAK = () => new Paragraph({ children: [new PageBreak()] });

// A quotation set off from the body text
const QUOTE = (text, attrib) => new Paragraph({
  spacing: { before: 120, after: 160, line: 280 },
  indent: { left: 420, right: 260 },
  border: { left: { style: BorderStyle.SINGLE, size: 14, color: BROWN, space: 14 } },
  children: [
    t(text, { size: 21, italics: true, color: INK }),
    ...(attrib ? [t('   — ' + attrib, { size: 18, color: MUTED })] : []),
  ],
});

const img = (file, w) => {
  const buf = fs.readFileSync(path.join(IMG, file));
  // PNG header: width/height are big-endian uint32 at byte 16 and 20
  const pw = buf.readUInt32BE(16), ph = buf.readUInt32BE(20);
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 60, after: 0 },
    children: [new ImageRun({ type: 'png', data: buf, transformation: { width: w, height: Math.round(w * ph / pw) } })],
  });
};

// ---------------------------------------------------------------- tables
const cell = (children, o = {}) => new TableCell({
  width: { size: o.w, type: WidthType.DXA },
  shading: { type: ShadingType.CLEAR, color: 'auto', fill: o.fill || 'FFFFFF' },
  margins: { top: 110, bottom: 110, left: 150, right: 150 },
  verticalAlign: VerticalAlign.TOP,
  borders: {
    top: { style: BorderStyle.SINGLE, size: 4, color: 'B9AF9E' },
    bottom: { style: BorderStyle.SINGLE, size: 4, color: 'B9AF9E' },
    left: { style: BorderStyle.SINGLE, size: 4, color: 'B9AF9E' },
    right: { style: BorderStyle.SINGLE, size: 4, color: 'B9AF9E' },
  },
  children,
});

const tcell = (text, o = {}) => cell(
  [new Paragraph({
    alignment: o.align || AlignmentType.LEFT,
    spacing: { after: 0, line: 280 },
    children: Array.isArray(text) ? text : [t(text, { size: o.size || 19, color: o.color || INK, bold: o.bold, italics: o.italics })],
  })], o);

const headerRow = (labels, widths, fill = NAVY) => new TableRow({
  tableHeader: true,
  children: labels.map((l, i) => tcell(l, { w: widths[i], fill, color: 'FFFFFF', bold: true, size: 19 })),
});

const table = (widths, rows) => new Table({
  columnWidths: widths,
  width: { size: widths.reduce((a, b) => a + b, 0), type: WidthType.DXA },
  rows,
});

// ================================================================ CONTENT
const children = [];
const add = (...x) => children.push(...x.flat());

// ------------------------------------------------- helper: fill-in line
const FILLIN = (label, o = {}) => new Paragraph({
  spacing: { before: o.before ?? 0, after: o.after ?? 220 },
  alignment: o.align || AlignmentType.LEFT,
  children: [
    t(label, { size: 22, color: INK, bold: true }),
    t('  ' + '.'.repeat(o.dots || 46), { size: 22, color: 'A9A296' }),
  ],
});

// =========================================================== 1. COVER
add(
  SPACER(500),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 40 }, children: [t('ASIAN INTERNATIONAL SCHOOL', { size: 40, bold: true, color: NAVY, characterSpacing: 40 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 300 }, children: [t('EDUCATION FOR LIFE', { size: 18, color: BROWN, characterSpacing: 90 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 500 }, border: { bottom: { style: BorderStyle.DOUBLE, size: 8, color: BROWN } }, children: [t('', { size: 2 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 120 }, children: [t('ENGLISH CORE (301)', { size: 22, color: PURPLE, characterSpacing: 70 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 460 }, children: [t('PROJECT WORK  ·  CLASS XII', { size: 26, bold: true, color: BROWN, characterSpacing: 30 })] }),
);
add(img('poster.png', 250));
add(
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 420, after: 140 }, children: [t('From Grand Central to the Silver Screen', { size: 32, bold: true, color: NAVY })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 520, line: 300 }, children: [t('An Intertextual Film Review Analysing Parallels Between Jack Finney’s ', { size: 21, italics: true, color: INK }), t('The Third Level', { size: 21, italics: true, bold: true, color: INK }), t(' and Modern Cinematic Portrayals of Psychological Refuge', { size: 21, italics: true, color: INK })] }),
  SPACER(260),
  FILLIN('Name', { dots: 40 }),
  FILLIN('Class & Section', { dots: 34 }),
  FILLIN('Roll Number', { dots: 37 }),
  FILLIN('Session', { dots: 39 }),
  BREAK(),
);

// =========================================================== 2. TITLE PAGE
add(
  SPACER(900),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 60 }, children: [t('TOPIC', { size: 19, color: BROWN, characterSpacing: 100 })] }),
  RULE(BROWN, 6),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 200, after: 200, line: 380 }, children: [t('From Grand Central to the Silver Screen', { size: 40, bold: true, color: NAVY })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 420, line: 340 }, children: [t('An Intertextual Film Review Analysing Parallels Between Jack Finney’s ', { size: 24, italics: true, color: INK }), t('The Third Level', { size: 24, italics: true, bold: true, color: INK }), t(' and Modern Cinematic Portrayals of Psychological Refuge', { size: 24, italics: true, color: INK })] }),
  RULE(BROWN, 6),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 300, after: 100 }, children: [t('FILM SELECTED FOR REVIEW', { size: 18, color: PURPLE, characterSpacing: 80 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 60 }, children: [t('Midnight in Paris', { size: 30, bold: true, italics: true, color: BROWN })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 700 }, children: [t('directed by Woody Allen  ·  2011', { size: 20, color: MUTED })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 60 }, children: [t('Subject: English Core (301)   ·   Class XII', { size: 20, color: INK })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 60 }, children: [t('Asian International School', { size: 20, color: INK })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 0 }, children: [t('Date of Submission: 30 November 2026', { size: 20, bold: true, color: NAVY })] }),
  BREAK(),
);

// =========================================================== 3. CERTIFICATE
add(
  SPACER(400),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 40 }, children: [t('CERTIFICATE', { size: 38, bold: true, color: NAVY, characterSpacing: 80 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 500 }, border: { bottom: { style: BorderStyle.SINGLE, size: 10, color: BROWN } }, children: [t('', { size: 2 })] }),
  new Paragraph({
    alignment: AlignmentType.JUSTIFIED, spacing: { after: 260, line: 420 },
    children: [
      t('This is to certify that ', { size: 23, color: INK }),
      t('.'.repeat(34), { size: 23, color: 'A9A296' }),
      t(' , a student of Class XII of Asian International School, has satisfactorily completed the Project Work in ', { size: 23, color: INK }),
      t('English Core (301)', { size: 23, bold: true, color: INK }),
      t(' prescribed by the Central Board of Secondary Education for the session ', { size: 23, color: INK }),
      t('.'.repeat(16), { size: 23, color: 'A9A296' }),
      t(' .', { size: 23, color: INK }),
    ],
  }),
  P('The project, titled “From Grand Central to the Silver Screen: An Intertextual Film Review Analysing Parallels Between Jack Finney’s The Third Level and Modern Cinematic Portrayals of Psychological Refuge”, is the candidate’s own work, carried out under my supervision. It has been completed within the prescribed timeline and is submitted in partial fulfilment of the requirements of the Internal Assessment.', { size: 23, line: 420, after: 260 }),
  P('The work has been examined and is found to be original, adequately referenced, and satisfactory in respect of content, presentation and language.', { size: 23, line: 420, after: 900 }),
  new Paragraph({
    spacing: { after: 0 },
    tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_W, leader: LeaderType.NONE }],
    children: [
      t('.'.repeat(36), { size: 22, color: 'A9A296' }),
      new TextRun({ children: [new Tab()] }),
      t('.'.repeat(36), { size: 22, color: 'A9A296' }),
    ],
  }),
  new Paragraph({
    spacing: { after: 0 },
    tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_W, leader: LeaderType.NONE }],
    children: [
      t('Signature of the Subject Teacher', { size: 19, color: MUTED }),
      new TextRun({ children: [new Tab()] }),
      t('Signature of the Principal', { size: 19, color: MUTED }),
    ],
  }),
  BREAK(),
);

// =========================================================== 4. ACKNOWLEDGEMENT
add(
  SPACER(300),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 40 }, children: [t('ACKNOWLEDGEMENT', { size: 36, bold: true, color: NAVY, characterSpacing: 60 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 420 }, border: { bottom: { style: BorderStyle.SINGLE, size: 10, color: BROWN } }, children: [t('', { size: 2 })] }),
  P('I wish to record my sincere gratitude to my English teacher, whose guidance shaped this project from a single prescribed question into an argument I actually wanted to make. The suggestion that a short story and a film could be read against each other, rather than merely beside each other, is the idea on which this entire project rests.', { size: 22, line: 360 }),
  P('I am grateful to the Principal of Asian International School for providing the academic environment and the library resources that made this study possible, and to the school for allowing a topic that asked for genuine opinion rather than reproduction.', { size: 22, line: 360 }),
  P('I thank my parents for their patience during the weeks in which this work was written, and for tolerating a great deal of talk about staircases that do not exist. I also thank my classmates, with whom I argued about the ending of Midnight in Paris more than once; several of the ideas in Part B are sharper because they disagreed with me first.', { size: 22, line: 360 }),
  P('Finally, my thanks to Jack Finney, who wrote a story in 1950 that seems to have been written about 2026, and whose ordinary commuter turned out to have a great deal to say to a student in Class XII.', { size: 22, line: 360, after: 800 }),
  new Paragraph({ alignment: AlignmentType.RIGHT, spacing: { after: 40 }, children: [t('.'.repeat(30), { size: 22, color: 'A9A296' })] }),
  new Paragraph({ alignment: AlignmentType.RIGHT, spacing: { after: 0 }, children: [t('Signature of the Candidate', { size: 19, color: MUTED })] }),
  BREAK(),
);

// =========================================================== 5. DECLARATION
add(
  SPACER(300),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 40 }, children: [t('DECLARATION', { size: 36, bold: true, color: NAVY, characterSpacing: 60 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 420 }, border: { bottom: { style: BorderStyle.SINGLE, size: 10, color: BROWN } }, children: [t('', { size: 2 })] }),
  P('I declare that the project submitted here is my own work and that it has not been copied, in whole or in part, from any printed or online source. Where the words of another writer have been used, they appear inside quotation marks and are attributed at the point of use; where an idea has been borrowed rather than quoted, the source is named in the Bibliography.', { size: 22, line: 360 }),
  P('All quotations from The Third Level have been checked directly against the prescribed NCERT text rather than taken from summaries or study guides, and the wording, punctuation and dates given in Part A are those of the printed story. Dialogue quoted from Midnight in Paris is given as spoken in the film; where a scene is described rather than quoted, it is identified clearly enough for the claim to be verified.', { size: 22, line: 360 }),
  P('The six plates in this project — the diagrams, the ticket illustration and the film poster in Part C — are entirely my own original designs. No copyrighted photograph, film still or promotional artwork has been reproduced anywhere in this file.', { size: 22, line: 360 }),
  P('The opinions offered in Part B.6 and Part D are my own, and I am prepared to defend them at the viva.', { size: 22, line: 360, after: 800 }),
  new Paragraph({ alignment: AlignmentType.RIGHT, spacing: { after: 40 }, children: [t('.'.repeat(30), { size: 22, color: 'A9A296' })] }),
  new Paragraph({ alignment: AlignmentType.RIGHT, spacing: { after: 0 }, children: [t('Signature of the Candidate', { size: 19, color: MUTED })] }),
);

const frontMatter = children.splice(0, children.length);

// ================================================================ BODY
const body = [];
const badd = (...x) => body.push(...x.flat());

const TOCLINE = (label, page, o = {}) => new Paragraph({
  spacing: { after: o.after ?? 90 },
  indent: { left: o.indent || 0 },
  tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_W, leader: LeaderType.DOT }],
  children: [
    t(label, { size: o.size || 21, bold: o.bold, color: o.color || INK }),
    new TextRun({ children: [new Tab()] }),
    t(String(page), { size: o.size || 21, bold: o.bold, color: o.color || INK }),
  ],
});

// ---------------------------------------------------------- CONTENTS (p1)
badd(
  H1('Contents'), RULE(),
  TOCLINE('Statement of Purpose, Objectives and Method', 2, { bold: true, color: NAVY }),
  SPACER(120),
  TOCLINE('PART A  ·  TEXTUAL FOUNDATION', 3, { bold: true, color: BROWN }),
  TOCLINE('A.1   The Author and the Text', 4, { indent: 300 }),
  TOCLINE('A.2   Summary of The Third Level', 5, { indent: 300 }),
  TOCLINE('Plate I — The Architecture of Escape', 6, { indent: 300, color: MUTED }),
  TOCLINE('A.3   Three Key Quotes Showing Escapism', 7, { indent: 300 }),
  TOCLINE('A.4   What Charley Escapes From — and What He Escapes To', 8, { indent: 300 }),
  TOCLINE('A.5   The Five Themes at a Glance', 9, { indent: 300 }),
  SPACER(120),
  TOCLINE('PART B  ·  FILM REVIEW: MIDNIGHT IN PARIS', 10, { bold: true, color: BROWN }),
  TOCLINE('B.1   Basic Information   ·   B.2   Plot in Brief', 11, { indent: 300 }),
  TOCLINE('B.3   Theme Connection', 12, { indent: 300 }),
  TOCLINE('B.4   Character Parallel', 14, { indent: 300 }),
  TOCLINE('Plate II — Two Men, Two Midnights', 15, { indent: 300, color: MUTED }),
  TOCLINE('B.5   Critical Comment', 16, { indent: 300 }),
  TOCLINE('Plate III — The Staircase With No Bottom', 17, { indent: 300, color: MUTED }),
  TOCLINE('B.6   Personal Response', 18, { indent: 300 }),
  TOCLINE('Plate IV — Why 2026 Wants a Third Level', 19, { indent: 300, color: MUTED }),
  SPACER(120),
  TOCLINE('PART C  ·  CREATIVE EXTENSION: POSTER DESIGN', 20, { bold: true, color: BROWN }),
  TOCLINE('Plate V — The Third Level (2026): Poster', 21, { indent: 300, color: MUTED }),
  TOCLINE('C.1   Designer’s Note', 22, { indent: 300 }),
  SPACER(120),
  TOCLINE('PART D  ·  CONCLUSION', 23, { bold: true, color: BROWN }),
  TOCLINE('D.1   Final Verdict', 24, { indent: 300 }),
  TOCLINE('Plate VI — Two Tickets', 25, { indent: 300, color: MUTED }),
  SPACER(120),
  TOCLINE('Knowledge and Experience Gained', 26, { bold: true, color: NAVY }),
  TOCLINE('Appendix I — Viva Voce Preparation', 27, { bold: true, color: NAVY }),
  TOCLINE('Appendix II — Glossary of Critical Terms', 29, { bold: true, color: NAVY }),
  TOCLINE('Bibliography and References', 30, { bold: true, color: NAVY }),
  BREAK(),
);

// ------------------------------------------- STATEMENT OF PURPOSE (p2)
badd(
  H1('Statement of Purpose, Objectives and Method'), RULE(),
  H2('Purpose', { before: 0 }),
  P('Jack Finney published The Third Level in 1950, five years after the end of a war that had rearranged the world. Seventy-six years later the story is still prescribed for study — not because a hidden staircase is a clever idea, but because the impulse that leads a man to look for one has not gone away. This project sets that story beside a film which stages the same impulse and then argues with it: Woody Allen’s Midnight in Paris (2011).'),
  P('The purpose is not to show that a film has “copied” a short story; the two have no connection of influence. It is to test one question across two texts, two countries and two centuries: when a person walks out of their own time, are they resting, or are they running away?'),
  H2('Objectives'),
  P([t('1.  ', { size: 22, bold: true, color: BROWN }), t('To read The Third Level closely and identify the textual evidence for Charley’s escapism.', { size: 22, color: INK })], { after: 80 }),
  P([t('2.  ', { size: 22, bold: true, color: BROWN }), t('To review Midnight in Paris as a film in its own right — plot, characters, and above all its ending.', { size: 22, color: INK })], { after: 80 }),
  P([t('3.  ', { size: 22, bold: true, color: BROWN }), t('To map the five prescribed themes of the story onto specific scenes and dialogue in the film.', { size: 22, color: INK })], { after: 80 }),
  P([t('4.  ', { size: 22, bold: true, color: BROWN }), t('To compare Charley with Gil Pender, and to classify the nature of each man’s escape.', { size: 22, color: INK })], { after: 80 }),
  P([t('5.  ', { size: 22, bold: true, color: BROWN }), t('To judge whether each text glorifies escapism or warns against it, arguing from the evidence of its ending.', { size: 22, color: INK })], { after: 80 }),
  P([t('6.  ', { size: 22, bold: true, color: BROWN }), t('To relate both texts to the pressures of 2026 and reach a defensible personal position.', { size: 22, color: INK })], { after: 80 }),
  H2('Method and Note on Sources'),
  P('The story has been read in the prescribed NCERT edition, and every quotation in this project has been checked word by word against that printed text rather than reproduced from memory or from a summary website. Dates, addresses and figures — 11 June 1894, 18 July 1894, 941 Willard Street, eight hundred dollars — are given exactly as the story gives them, because in a story about time the details are the argument.'),
  P('The film has been viewed with attention to the two conversations that carry its thinking: Paul’s lecture on nostalgia early in the film, and Gil’s final exchange with Adriana. Where dialogue is quoted, it is given as spoken; where a scene is described instead, it is identified precisely enough for the claim to be checked. Secondary reading has been used sparingly, and is listed in full in the Bibliography.'),
  BREAK(),
);

// ---------------------------------------------------------- PART DIVIDER
const DIVIDER = (part, title, epigraph, source) => [
  SPACER(1600),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 100 }, children: [t(part, { size: 20, color: BROWN, characterSpacing: 140 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 60 }, children: [t(title, { size: 46, bold: true, color: NAVY })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 200, after: 700 }, border: { bottom: { style: BorderStyle.SINGLE, size: 10, color: BROWN } }, children: [t('', { size: 2 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 100, line: 340 }, indent: { left: 900, right: 900 }, children: [t('“' + epigraph + '”', { size: 24, italics: true, color: INK })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 0 }, children: [t('— ' + source, { size: 19, color: MUTED })] }),
  BREAK(),
];

badd(DIVIDER('PART A', 'Textual Foundation', 'I’ve been on the third level of the Grand Central Station.', 'Jack Finney, The Third Level'));

// ------------------------------------------------- A.1 AUTHOR & TEXT (p4)
badd(
  KICKER('PART A  ·  SECTION 1'),
  H1('The Author and the Text'), RULE(),
  P('Jack Finney (1911–1995) was an American novelist and short-story writer whose speciality was the quiet impossibility: an ordinary person, in an ordinary street, who steps sideways out of their own decade. He is remembered today for two very different books — The Body Snatchers (1955), filmed repeatedly as Invasion of the Body Snatchers, and Time and Again (1970), a novel of nineteenth-century New York that is still read as one of the finest time-travel stories in English.'),
  P('The Third Level first appeared in 1950 and gave its title to his 1957 collection. What distinguishes Finney from most writers shelved beside him is that his science fiction contains almost no science. There is no machine in this story, no theory, no explanation of any kind. A man takes a wrong corridor and comes out in 1894. Finney is not interested in how it happens; he is interested in why a man would want it to.'),
  P('That refusal to explain is the reason the story pairs so naturally with a film. Cinema is equally willing to open a door and never account for it. Midnight in Paris offers no more mechanism than Finney does: a car arrives at midnight, and the audience is asked to accept it exactly as Charley’s readers are asked to accept the staircase. In both works the supernatural is a device for getting at something entirely ordinary — a person who is unhappy in their own time and would rather be somewhere else.'),
  P('The story is also, quietly, a period document. Written five years after the Second World War, by a man of thirty-nine who had lived through both a depression and a global conflict, it takes for granted a reader who understands what “insecurity, fear, war, worry” refers to. What makes it worth teaching in 2026 is that the sentence has survived its own context. The list still describes something; only its contents have been replaced.'),
  BREAK(),
);

// ------------------------------------------------------- A.2 SUMMARY (p5)
badd(
  KICKER('PART A  ·  SECTION 2'),
  H1('Summary of The Third Level'), RULE(),
  new Paragraph({
    spacing: { after: 200, line: 360 },
    alignment: AlignmentType.JUSTIFIED,
    shading: { type: ShadingType.CLEAR, color: 'auto', fill: CREAM },
    border: { top: { style: BorderStyle.SINGLE, size: 8, color: BROWN }, bottom: { style: BorderStyle.SINGLE, size: 8, color: BROWN }, left: { style: BorderStyle.SINGLE, size: 8, color: BROWN }, right: { style: BorderStyle.SINGLE, size: 8, color: BROWN } },
    indent: { left: 200, right: 200 },
    children: [t('Charley, an ordinary thirty-one-year-old New Yorker, insists that Grand Central Station has three levels, though the railroads admit only two. Hurrying to the subway one night, he takes a corridor that slants downward and emerges into a smaller concourse lit by open-flame gaslights, with brass spittoons, derby hats and a copy of The World dated 11 June 1894. He tries to buy two one-way tickets to Galesburg, Illinois; his modern currency is refused. He never finds the corridor again. Later, a first-day cover in his stamp collection yields a letter from Sam, his psychiatrist, written from Galesburg in 1894.', { size: 22, color: INK })],
  }),
  new Paragraph({ alignment: AlignmentType.RIGHT, spacing: { after: 320 }, children: [t('[ 99 words ]', { size: 17, italics: true, color: MUTED })] }),
  H2('The shape of the story', { before: 0 }),
  P('Finney arranges the plot as a series of tightening confirmations. The opening is an assertion nobody believes; the middle is an experience the reader shares with Charley in convincing physical detail — gaslight, spittoons, leg-of-mutton sleeves, a Currier & Ives locomotive; and the ending is documentary evidence delivered by the one character professionally qualified to disbelieve it.'),
  P('It is worth noticing how little happens. Charley finds the third level, fails to buy a ticket because his money is the wrong size, and leaves. That failure is the entire plot. Everything that gives the story its force — the search, the three hundred dollars changed into old-style bills at a loss, Louisa’s worry, Sam’s disappearance — hangs off a single unsuccessful transaction at a ticket window.'),
  BREAK(),
);

// -------------------------------------------------------- PLATE I (p6)
badd(
  KICKER('PLATE I'),
  H1('The Architecture of Escape'), RULE(),
  img('fig1.png', 600),
  CAPTION('Plate I. The three levels of Grand Central read as three levels of the self. Original diagram.'),
  SPACER(240),
  P('The station is a gift to a writer because it is already a metaphor. Finney notes that Grand Central has, for years, been “an exit, a way of escape” for a great many people — and then invents one more exit than the building officially has. The diagram above sets the architecture against the psychology: the further Charley descends from the street, the further he descends from the version of himself that the street requires.'),
  BREAK(),
);

// -------------------------------------------------------- A.3 QUOTES (p7)
const qw = [4400, 4671];
badd(
  KICKER('PART A  ·  SECTION 3'),
  H1('Three Key Quotes Showing Escapism'), RULE(),
  table(qw, [
    headerRow(['Quotation from the text', 'What it reveals'], qw, NAVY),
    new TableRow({ children: [
      tcell([t('“…the modern world is full of insecurity, fear, war, worry and all the rest of it, and that I just want to escape. Well, who doesn’t? Everybody I know wants to escape…”', { size: 19, italics: true, color: INK })], { w: qw[0], fill: PALEBROWN }),
      tcell('Sam’s diagnosis, reported by Charley himself. The story names its own theme in the first paragraph — and then Charley half-concedes it. “Well, who doesn’t?” universalises the wish in order to excuse it: if everyone wants to escape, wanting it cannot be a symptom.', { w: qw[1] }),
    ] }),
    new TableRow({ children: [
      tcell([t('“My stamp collecting, for example; that’s a ‘temporary refuge from reality.’”', { size: 19, italics: true, color: INK })], { w: qw[0], fill: PALEBROWN }),
      tcell('Charley quotes the clinical phrase with visible irritation, and the inverted commas are his. He is not disputing that he needs a refuge; he is disputing the word “temporary”. The stamps are the mild, permitted version of the third level — and it is a stamp, in the end, that brings Sam’s letter to him.', { w: qw[1] }),
    ] }),
    new TableRow({ children: [
      tcell([t('“To be back there with the First World War still twenty years off, and World War II over forty years in the future… I wanted two tickets for that.”', { size: 19, italics: true, color: INK })], { w: qw[0], fill: PALEBROWN }),
      tcell('The most revealing sentence in the story. Charley does not describe 1894 by what it contains but by what has not yet happened to it. He is not buying a destination; he is buying an absence. His golden age is defined entirely by the catastrophes it is still innocent of.', { w: qw[1] }),
    ] }),
  ]),
  SPACER(280),
  P('Read together, the three quotations move from the general to the exact. The first states the condition, the second gives it a socially acceptable outlet, and the third reveals what the outlet is really reaching for.'),
  BREAK(),
);

// ---------------------------------------------------------- A.4 (p8)
badd(
  KICKER('PART A  ·  SECTION 4'),
  H1('What Charley Escapes From — and What He Escapes To'), RULE(),
  H2('From', { before: 0 }),
  P('Sam supplies the official answer: the modern world is “full of insecurity, fear, war, worry and all the rest of it”. Written in 1950, that is a description of a generation which had lived through a depression and two wars and had just been handed the atomic bomb.'),
  P('But the story locates the unhappiness somewhere smaller than history. Charley works late at the office. He takes the subway because it is faster than the bus. He notes, with no apparent distress, that he “passed a dozen men who looked just like me”. What he is escaping is not only a dangerous century but an interchangeable life inside it — a tan gabardine suit in a crowd of tan gabardine suits. And he denies all of it in the same breath: “I wasn’t trying to escape from anything; I just wanted to get home to Louisa, my wife.” Minutes later he is at a ticket window asking for 1894.'),
  H2('To'),
  P('Not “the past” in general, but a particular past he has inherited. Galesburg is his grandfather’s town and his own school town, remembered as a place of “big old frame houses, huge lawns, and tremendous trees whose branches meet overhead and roof the streets”, where summer evenings were twice as long and the men sat out smoking cigars while the women waved palm-leaf fans.'),
  P('This is the crucial point about Charley’s nostalgia: it is second-hand. He has never lived in 1894. He is homesick for a place he knows from his grandfather’s stories and his grandfather’s stamp album — which is precisely why it can be perfect. A remembered past can disappoint you; an inherited one cannot, because nobody has ever had to live in it.'),
  BREAK(),
);

// ---------------------------------------------------------- A.5 (p9)
const t5 = [2500, 6571];
badd(
  KICKER('PART A  ·  SECTION 5'),
  H1('The Five Themes at a Glance'), RULE(),
  P('The five themes prescribed for study are set out below in the form in which they are used throughout Part B, so that each may be traced from the story into the film.'),
  SPACER(80),
  table(t5, [
    headerRow(['Theme', 'How the story carries it'], t5, BROWN),
    new TableRow({ children: [tcell('1.  Escapism', { w: t5[0], fill: PALEBROWN, bold: true }), tcell('Charley’s retreat from “insecurity, fear, war, worry” into 1894 Galesburg. Grand Central itself has long been “an exit, a way of escape”.', { w: t5[1] })] }),
    new TableRow({ children: [tcell('2.  Nostalgia for a simpler past', { w: t5[0], fill: PALEBROWN, bold: true }), tcell('A pre-war America idealised as peaceful and humane — long summer evenings, fireflies, tree-roofed streets — and dated by the two wars that have not yet happened.', { w: t5[1] })] }),
    new TableRow({ children: [tcell('3.  Time as a psychological construct', { w: t5[0], fill: PALEBROWN, bold: true }), tcell('The third level is never explained. Sam calls it “a waking-dream wish fulfillment”; the story then produces evidence that it is real, and leaves the contradiction standing.', { w: t5[1] })] }),
    new TableRow({ children: [tcell('4.  Modern anxiety', { w: t5[0], fill: PALEBROWN, bold: true }), tcell('Post-war disillusionment, the anonymity of the commuter, the sense of being one of a dozen identical men — a diagnosis that has outlived its own decade.', { w: t5[1] })] }),
    new TableRow({ children: [tcell('5.  Friendship and belief', { w: t5[0], fill: PALEBROWN, bold: true }), tcell('Sam moves from professional scepticism to belief so complete that he emigrates; Louisa’s rational rejection softens only when the letter is produced. Belief, in this story, is contagious.', { w: t5[1] })] }),
  ]),
  BREAK(),
);

// ============================================================== PART B
badd(DIVIDER('PART B', 'Film Review', 'The name for this fallacy is Golden Age thinking.', 'Paul, Midnight in Paris'));

// ------------------------------------------------------- B.1 + B.2 (p11)
const bw = [2400, 6671];
badd(
  KICKER('PART B  ·  SECTION 1'),
  H1('Basic Information'), RULE(),
  table(bw, [
    new TableRow({ children: [tcell('Film', { w: bw[0], fill: PALEBLUE, bold: true }), tcell([t('Midnight in Paris', { size: 19, italics: true, bold: true, color: INK })], { w: bw[1] })] }),
    new TableRow({ children: [tcell('Director', { w: bw[0], fill: PALEBLUE, bold: true }), tcell('Woody Allen', { w: bw[1] })] }),
    new TableRow({ children: [tcell('Year', { w: bw[0], fill: PALEBLUE, bold: true }), tcell('2011', { w: bw[1] })] }),
    new TableRow({ children: [tcell('Genre', { w: bw[0], fill: PALEBLUE, bold: true }), tcell('Romantic fantasy / comedy-drama, with an element of unexplained time travel', { w: bw[1] })] }),
    new TableRow({ children: [tcell('One-line plot', { w: bw[0], fill: PALEBLUE, bold: true }), tcell('A dissatisfied Hollywood screenwriter, holidaying in Paris with a fiancée he is drifting away from, discovers that at the stroke of midnight an antique car will carry him into the Paris of the 1920s.', { w: bw[1] })] }),
    new TableRow({ children: [tcell('Why this film', { w: bw[0], fill: PALEBLUE, bold: true }), tcell('It is the closest cinematic relative of The Third Level — an unexplained doorway into an idealised earlier era — and the only one of the suggested films whose ending directly argues about whether such a doorway should be used.', { w: bw[1] })] }),
  ]),
  H2('B.2   Plot in Brief'),
  P('Gil Pender, a successful Hollywood screenwriter who despises his own success, is in Paris with his fiancée Inez and her wealthy parents. He is struggling with a first novel about a man who works in a nostalgia shop, and he believes he was born in the wrong decade. Walking back alone at midnight, lost and a little drunk, he is picked up by an antique Peugeot and set down in the Paris of the 1920s, where he meets the Fitzgeralds, Hemingway, Gertrude Stein, Picasso and Dalí. He returns night after night, and falls in love with Adriana. When the two of them travel further back to the Belle Époque and Adriana refuses to leave, Gil finally understands what his own longing is worth. He returns to the present for good, ends his engagement, stays in Paris, and walks off into the rain.'),
  new Paragraph({ alignment: AlignmentType.RIGHT, spacing: { after: 0 }, children: [t('[ 142 words ]', { size: 17, italics: true, color: MUTED })] }),
  BREAK(),
);

// ------------------------------------------------------- B.3 THEMES (p12)
const cw = [2450, 3300, 3321];
const themeRow = (a, b, c, fill) => new TableRow({ children: [
  tcell([t(a, { size: 19, bold: true, color: INK })], { w: cw[0], fill }),
  tcell(b, { w: cw[1] }),
  tcell([t(c, { size: 18, italics: true, color: INK })], { w: cw[2] }),
] });

badd(
  KICKER('PART B  ·  SECTION 3'),
  H1('Theme Connection'), RULE(),
  P('Each of the five prescribed themes is traced from the story into the film, with the scene or line of dialogue that carries it.'),
  SPACER(80),
  table(cw, [
    headerRow(['Theme in The Third Level', 'How it appears in the film', 'Evidence: scene / dialogue'], cw, NAVY),
    themeRow('1.  Escapism',
      'Gil escapes nightly from an engagement and a career that no longer fit him. As with Charley, the exit is a piece of ordinary city furniture — a street corner instead of a corridor — and the film is careful to show that he was unhappy before he was ever transported.',
      'On the stone steps of the Rue Montagne Sainte-Geneviève, a bell strikes twelve and an antique Peugeot pulls up; its passengers wave him in. No mechanism is offered, then or ever.', PALEBROWN),
    themeRow('2.  Nostalgia for a simpler past',
      'Gil’s golden age is the Paris of the 1920s, a decade he knows entirely from books — exactly as Charley knows 1894 entirely from his grandfather. Both men are homesick for a period they have never lived in.',
      'Gil is writing a novel about a man who works in a nostalgia shop. Paul diagnoses him publicly: “Golden Age thinking — the erroneous notion that a different time period is better than the one one’s living in.”', PALEBLUE),
    themeRow('3.  Time as a psychological construct',
      'The film refuses to say whether the journeys are real. There is no device and no rule; the car simply comes. What is presented with total clarity is the mental state that precedes it — which is precisely Finney’s structure.',
      'The detective hired to follow Gil vanishes into the court of Louis XIV and is never recovered — a joke that only works because the film has declined to establish any physics at all.', PALEBROWN),
    themeRow('4.  Modern anxiety',
      'Charley’s “insecurity, fear, war, worry” becomes something smaller and more corrosive: the sense of being surrounded by people whose values you do not share and cannot argue with.',
      'Inez’s preference for Malibu; her father’s politics and his hiring of the detective; and above all Paul, the pedant who is confidently wrong at the Rodin museum while Gil says nothing.', PALEBLUE),
    themeRow('5.  Friendship and belief',
      'The story splits its cast into a believer (Sam) and a sceptic (Louisa). The film does the same, and then inverts the outcome: its believer is the one who is left behind in the past, while the sceptics are simply left.',
      'Adriana chooses to remain in the Belle Époque, as Sam remains in Galesburg. But where Sam writes “keep looking”, Gil walks away from Adriana — and from the invitation.', PALEBROWN),
  ]),
  SPACER(240),
  P('The table makes one asymmetry visible. On the first four themes the two works agree almost line for line. On the fifth they separate completely — and that separation is the subject of Section B.5.'),
  BREAK(),
);

// ---------------------------------------------------- B.4 CHARACTER (p14)
badd(
  KICKER('PART B  ·  SECTION 4'),
  H1('Character Parallel'), RULE(),
  P('Charley and Gil Pender are the same man at two different stages of the same illness. Both are competent professionals; both are engaged or married to a woman who is entirely reasonable and entirely unsuited to them; both have a harmless hobby that is really a rehearsal for leaving — Charley’s stamps, Gil’s unfinished novel about a nostalgia shop. Neither is running from disaster. Both are running from adequacy.'),
  H2('Is the escape voluntary, psychological, or supernatural?'),
  P('In both works the honest answer is that it is staged as supernatural, experienced as voluntary, and caused by something psychological.'),
  P('Neither text supplies a mechanism. Finney never explains the corridor; Allen never explains the Peugeot, and pointedly refuses to — there is no dial, no device and no rule about how the journeys work. Both writers withhold the machinery because the machinery is not the subject. What each explains with great care is the state of mind that precedes the journey. Charley is described as unhappy before he is ever lost; Gil is visibly unhappy before the clock strikes.'),
  P('The real difference lies in the will. Charley stumbles onto the third level once, by accident, and then spends years trying to repeat an accident — drawing three hundred dollars out of the bank and losing a third of it converting the notes into old-style bills. His escape becomes involuntary in the worst sense: a compulsion he cannot command. Gil’s is fully voluntary and fully repeatable. He knows the corner and he knows the hour, and he goes back because he chooses to.'),
  P('The paradox is worth stating plainly, because it is the heart of the comparison. It is the man who can leave whenever he likes who finds it possible to stop. Charley, who cannot get back, is the one who can never let go.'),
  BREAK(),
);

// ------------------------------------------------------- PLATE II (p15)
badd(
  KICKER('PLATE II'),
  H1('Two Men, Two Midnights'), RULE(),
  img('fig2.png', 600),
  CAPTION('Plate II. Charley and Gil Pender compared across five points, with the shape of each man’s journey. Original diagram.'),
  BREAK(),
);

// ------------------------------------------------------- B.5 CRITICAL (p16)
badd(
  KICKER('PART B  ·  SECTION 5'),
  H1('Critical Comment'), RULE(),
  P([t('Finney glorifies escapism. Allen warns against it. The evidence is in the two endings.', { size: 22, bold: true, color: NAVY })]),
  H2('The story ends with proof'),
  P('Sam has gone, and the letter he leaves behind is not a hint but a receipt: mailed from 941 Willard Street, Galesburg, postmarked 18 July 1894, and sitting undisturbed inside a first-day cover in Charley’s own collection for over half a century. Its final line is an instruction.'),
  QUOTE('Keep looking till you find the third level! It’s worth it, believe me!', 'Sam’s letter'),
  P('The last joke seals the argument. Sam was Charley’s psychiatrist — the one man professionally obliged to call the third level a delusion — and he has emigrated to it, taking eight hundred dollars in old-style currency to set up a hay, feed and grain business. Finney rewards the seeker and retires the sceptic. The story closes on the position that Charley was right all along, and that Louisa was wrong to ask him to stop.'),
  H2('The film ends with a refusal'),
  P('Allen builds the same fantasy with more charm, and then dismantles it in a single scene. When Adriana chooses to stay in the Belle Époque, Gil hears his own argument spoken back to him by somebody else, and it collapses under him. He tells her what he has just understood: that the present is “a little unsatisfying, because life’s a little unsatisfying”, and that whichever era she settles in will curdle into an ordinary present soon enough. The film has already named the fallacy in Paul’s lecture on Golden Age thinking — and the sting is that Paul, who is insufferable and wrong about everything else, is right about this.'),
  P('Gil’s final act is not to travel but to stay. He ends his engagement, remains in Paris, and walks off into the rain with a woman who lives in 2010. Finney gives his hero a ticket; Allen gives his hero a reason to hand it back.'),
  BREAK(),
);

// ------------------------------------------------------ PLATE III (p17)
badd(
  KICKER('PLATE III'),
  H1('The Staircase With No Bottom'), RULE(),
  img('fig3.png', 600),
  CAPTION('Plate III. The infinite regress that Midnight in Paris exposes and The Third Level never reaches. Original diagram.'),
  SPACER(200),
  P('This is the argument the film has that the story does not. Finney allows Charley one step down and stops there. Allen takes the same step, and then asks what happens if you take another — and the answer destroys the whole enterprise, because the second step proves that there is no floor.'),
  BREAK(),
);

// ------------------------------------------------------ B.6 PERSONAL (p18)
badd(
  KICKER('PART B  ·  SECTION 6'),
  H1('Personal Response'), RULE(),
  P([t('Would I take the third level if it were offered? Yes — and I would insist on a return ticket.', { size: 22, bold: true, color: NAVY })]),
  P('That is not a way of avoiding the question; it is the whole distinction the two texts have been circling. Charley asks the clerk for “two coach tickets, one way”. Gil climbs into the Peugeot every midnight and climbs out again every morning. Both men leave. Only one of them intends to come back, and that single difference decides whether what they are doing is rest or ruin.'),
  H2('Why 2026 would want the staircase'),
  P('Finney’s list still reads accurately — “insecurity, fear, war, worry” — only the nouns have been replaced. Ours arrive in three shifts.'),
  P('There is exam pressure: a fortnight of papers quietly treated as a verdict on an entire person, with a cut-off mark standing in for a future. There is the feed: a scroll engineered so that no evening is ever quite sufficient, because it is always showing you a better one — edited, and belonging to somebody else. And there is the newest of the three, which Finney could not have imagined: the low, constant question of whether the profession you are being examined for will still exist by the time you reach it, now that a machine can produce a competent draft of a great deal of it. Charley at least knew what he was training to become.'),
  H2('And so'),
  P('The impulse is legitimate and I refuse to be embarrassed by it. I use my own third levels most days — a novel, an album, a film at one in the morning — and I think anyone who claims otherwise is either lying or not paying attention.'),
  P('What I would not do is what Charley does, which is to convert a refuge into an address. The moment he asks for a one-way ticket, the escape stops being a rest from his life and becomes a replacement for it. A staircase is a very good thing to have. It is a terrible thing to live on.'),
  BREAK(),
);

// ------------------------------------------------------- PLATE IV (p19)
badd(
  KICKER('PLATE IV'),
  H1('Why 2026 Wants a Third Level'), RULE(),
  img('fig4.png', 600),
  CAPTION('Plate IV. Finney’s 1950 diagnosis, translated into the pressures named in Section B.6. Original diagram.'),
  BREAK(),
);

// ============================================================== PART C
badd(DIVIDER('PART C', 'Creative Extension', 'Two levels take you home. The third takes you back.', 'Tagline written for this project'));

// --------------------------------------------------------- POSTER (p21)
badd(
  KICKER('PLATE V  ·  PART C'),
  H1('The Third Level (2026) — Poster Design'), RULE(),
  img('poster.png', 500),
  CAPTION('Plate V. Original poster design for an imagined 2026 film adaptation of The Third Level.'),
  BREAK(),
);

// ------------------------------------------------- C.1 DESIGNER'S NOTE (p22)
badd(
  KICKER('PART C  ·  SECTION 1'),
  H1('Designer’s Note'), RULE(),
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 260, line: 320 },
    shading: { type: ShadingType.CLEAR, color: 'auto', fill: CREAM },
    border: { top: { style: BorderStyle.SINGLE, size: 8, color: BROWN }, bottom: { style: BorderStyle.SINGLE, size: 8, color: BROWN }, left: { style: BorderStyle.SINGLE, size: 8, color: BROWN }, right: { style: BorderStyle.SINGLE, size: 8, color: BROWN } },
    indent: { left: 200, right: 200 },
    children: [
      t('TAGLINE', { size: 17, color: PURPLE, characterSpacing: 90 }),
      new TextRun({ text: 'Two levels take you home.', font: BODY, size: 28, bold: true, color: NAVY, break: 2 }),
      new TextRun({ text: 'The third takes you back.', font: BODY, size: 28, bold: true, italics: true, color: BROWN, break: 1 }),
    ],
  }),
  P('The tagline had to do two jobs in nine words: state the premise for anyone who has not read the story, and carry its double meaning. “Takes you back” is the ordinary English for nostalgia and the literal description of what the staircase does. The line also sets up the contrast the whole project turns on — the first sentence is about going home, the second is about not.'),
  H2('The design decisions'),
  P([t('The arch. ', { size: 22, bold: true, color: BROWN }), t('Grand Central’s Beaux-Arts window is the most recognisable thing in the story’s setting, so it becomes the frame — a doorway rather than a building. The clock set into the keystone is the station’s other famous feature and the poster’s only direct statement of the theme.', { size: 22, color: INK })]),
  P([t('The light. ', { size: 22, bold: true, color: BROWN }), t('Everything above and around the arch is cold blue; everything beyond it is warm. That is Charley’s account of the two worlds, reduced to a single visual decision. The warm light spills forward across the floor towards the viewer, because the story’s real question is whether we would walk into it.', { size: 22, color: INK })]),
  P([t('The figure. ', { size: 22, bold: true, color: BROWN }), t('He is small, silhouetted, and turned away — a straw-hatted commuter with a briefcase, one of the “dozen men who looked just like me”. Making him anonymous is the point: the story insists that nothing about Charley is special, and the poster should not contradict the story.', { size: 22, color: INK })]),
  P([t('The palette. ', { size: 22, bold: true, color: BROWN }), t('The whole project, plates included, is restricted to the colours permitted by the project guidelines — blue, black, brown, purple and sky blue. The warm tones in the arch are brown and its lighter tints rather than gold, so that the file remains within the stated rules while still reading as gaslight.', { size: 22, color: INK })]),
  P([t('The credit block. ', { size: 22, bold: true, color: BROWN }), t('It names only Jack Finney, who genuinely wrote the story, and states plainly that this is an imagined adaptation made for a school project. No real director, studio or actor has been credited to a film that does not exist.', { size: 22, color: INK })]),
  BREAK(),
);

// ============================================================== PART D
badd(DIVIDER('PART D', 'Conclusion', 'Life’s a little unsatisfying.', 'Gil Pender, Midnight in Paris'));

// ------------------------------------------------------ D.1 VERDICT (p24)
badd(
  KICKER('PART D  ·  SECTION 1'),
  H1('Final Verdict'), RULE(),
  P([t('In 2026 we do not need more third levels. We need better ones — and we need to stop confusing them with exits.', { size: 22, bold: true, color: NAVY })]),
  P('Finney and Allen agree on the diagnosis and part company on the prescription. Both accept that the present is genuinely difficult, and that the wish to step out of it is neither cowardly nor rare. “Well, who doesn’t?” says Charley, and he is right. Where they differ is on what that wish entitles a person to. Finney lets his hero keep the fantasy and rewards it with proof. Allen lets his hero test the fantasy to destruction and rewards him with a wet street in the present tense.'),
  P('The film is the more useful teacher precisely because it takes the fantasy more seriously. Allen never argues that the 1920s were dull; he shows us that they were magnificent, and then shows us Adriana finding them dull anyway. That is the point Charley never reaches. Had he ever got his two tickets, he would have arrived in Galesburg, unpacked, and within a year begun to notice that his neighbours were talking wistfully about the eighteen-fifties.'),
  H2('Coping and escaping'),
  P('The distinction the two texts hand us is not between staying and leaving. Both men leave, and so does everyone who has ever opened a novel at the end of a bad week. The distinction is what is printed on the ticket.'),
  P('Coping is an escape with a return on it: the hour of music, the film, the walk, the book — things you go into knowing that you will come out, and that the coming out is the point. Escapism is the identical journey with the return torn off. Finney’s hero, who is the more sympathetic of the two men, is also the one who gets this wrong; and the story is generous enough to let him be rewarded for it, which is exactly why the film is needed as a corrective.'),
  P('Charley’s tragedy is not that he found a staircase. It is that he asked for a one-way ticket at the bottom of it. The test for any of us — in 1894, in 1950, or in the examination hall in 2026 — is not whether we go down. It is whether we can still find the stairs going up.'),
  BREAK(),
);

// -------------------------------------------------------- PLATE VI (p25)
badd(
  KICKER('PLATE VI'),
  H1('Two Tickets'), RULE(),
  img('fig5.png', 600),
  CAPTION('Plate VI. The distinction on which Part D turns, set out as the two tickets the texts actually describe. Original diagram.'),
  BREAK(),
);

// ---------------------------------------------------- REFLECTION (p26)
badd(
  H1('Knowledge and Experience Gained'), RULE(),
  P('The most useful thing this project taught me had nothing to do with either text. It was that a quotation you are certain of is often slightly wrong. I began with three remembered lines from The Third Level and found, on checking them against the printed story, that two were paraphrases picked up from study notes rather than Finney’s actual sentences. Every quotation in this file was afterwards verified word by word against the text, and the discipline changed the analysis: the phrase “two coach tickets, one way” — which I had never noticed — became the hinge of the entire conclusion.'),
  P('I also learned what an intertextual comparison is actually for. My first plan was to list similarities between the story and the film, which would have produced a competent and completely uninteresting project. The work only became worth doing when the two texts began to disagree. The similarities established that they were talking about the same thing; the disagreement over their endings was where anything could be said.'),
  P('Third, I found that the strongest argument in a piece of criticism is usually the one that concedes something. Admitting that Charley is the more sympathetic figure, and that Finney’s ending is more emotionally satisfying than Allen’s, made the case against escapism stronger rather than weaker — because it stopped being a case against a straw man.'),
  P('Finally, the project made me a more suspicious reader of my own preferences. It is very easy to agree with Paul’s speech about Golden Age thinking while quietly exempting yourself from it. Working through Adriana’s choice honestly meant recognising that I have a golden age of my own, and that it would disappoint me within a fortnight of arriving.'),
  BREAK(),
);

// ------------------------------------------------- APPENDIX I: VIVA (p27)
const vw = [4400, 4671];
const vivaRow = (q, a, fill) => new TableRow({ children: [
  tcell([t(q, { size: 19, bold: true, color: NAVY })], { w: vw[0], fill }),
  tcell(a, { w: vw[1] }),
] });

badd(
  H1('Appendix I — Viva Voce Preparation'), RULE(),
  P('The Internal Assessment carries five marks for the viva. The questions below are those most likely to be asked on this topic, with the evidence needed to answer each in a sentence or two.'),
  SPACER(80),
  table(vw, [
    headerRow(['Question', 'Answer, with evidence'], vw, NAVY),
    vivaRow('Why did you choose Midnight in Paris rather than Interstellar or Walter Mitty?',
      'Because it is the only suggested film whose ending takes a position on escapism. Interstellar’s escape is physical, Mitty’s is daydream; only Allen’s film sends its hero into an idealised past and then argues him out of it, which is what makes a comparison with Finney productive rather than decorative.', PALEBLUE),
    vivaRow('Is the third level real?',
      'The story deliberately refuses to settle it. Sam calls it “a waking-dream wish fulfillment”, yet the first-day cover postmarked 18 July 1894 is physical evidence inside the fiction. Finney wants both readings alive at once — which is itself the point about time as a psychological construct.', PALEBROWN),
    vivaRow('What is the significance of Sam being a psychiatrist?',
      'It is the story’s final joke and its strongest endorsement of Charley. The one character qualified to diagnose the delusion is the one who emigrates into it — and gives up psychiatry for a hay, feed and grain business, the trade he says he always wished he could follow.', PALEBLUE),
    vivaRow('Why does Charley’s money fail?',
      'The old-style bills are “half again as big” as modern notes, so the clerk takes him for a swindler. Practically, it is what stops him leaving. Thematically, it is the story insisting that the past will not simply accept you: you cannot buy your way into another era with the currency of your own.', PALEBROWN),
    vivaRow('What does Louisa represent?',
      'The rational rejection of escape. She is not unsympathetic — she is worried, and she asks him to stop looking, and for a while he does. Her conversion at the end, when the letter appears, is what turns Finney’s ending into an endorsement rather than a warning.', PALEBLUE),
  ]),
  BREAK(),
);

badd(
  H1('Appendix I — continued'), RULE(),
  table(vw, [
    headerRow(['Question', 'Answer, with evidence'], vw, NAVY),
    vivaRow('What is “Golden Age thinking”?',
      'Paul’s term in the film for “the erroneous notion that a different time period is better than the one one’s living in”. The film then proves it structurally: Gil idealises the 1920s, Adriana idealises the Belle Époque, and the Belle Époque artists idealise the Renaissance.', PALEBROWN),
    vivaRow('Does the film glorify escapism or warn against it?',
      'It warns. Gil abandons Adriana in the Belle Époque, ends his engagement, and stays in the present. His last scene is a walk in the rain in 2010 — the ordinary present, chosen deliberately over a golden age he was free to keep.', PALEBLUE),
    vivaRow('What is the difference between coping and escaping, in your own words?',
      'The return ticket. Coping is leaving with the intention of coming back — a book, a film, an hour of music. Escapism is the same journey with no return printed on it. Charley asks the clerk for “two coach tickets, one way”; that phrase is the whole distinction.', PALEBROWN),
    vivaRow('Why is Charley’s nostalgia described as “second-hand”?',
      'He has never lived in 1894. Galesburg is his grandfather’s town and his own school town, known through stories and a stamp album. An inherited past cannot disappoint you, because nobody has had to live in it — which is precisely why it can be perfect.', PALEBLUE),
    vivaRow('Both texts refuse to explain the mechanism. Why does that matter?',
      'Because it tells you what each writer is actually interested in. Finney gives no theory of the corridor and Allen gives no rules for the Peugeot; both spend their attention instead on the unhappiness that precedes the journey. The fantasy is a device, not a subject.', PALEBROWN),
    vivaRow('Which is the better work?',
      'Finney’s is the better constructed — the whole story turns on one failed transaction at a ticket window, and the ending is beautifully sprung. Allen’s is the more honest, because it follows the fantasy one step further than is comfortable and admits what it finds there.', PALEBLUE),
  ]),
  BREAK(),
);

// ------------------------------------------ APPENDIX II: GLOSSARY (p29)
const gw = [2500, 6571];
const gloss = (term, def, fill) => new TableRow({ children: [
  tcell([t(term, { size: 19, bold: true, color: NAVY })], { w: gw[0], fill }),
  tcell(def, { w: gw[1] }),
] });

badd(
  H1('Appendix II — Glossary of Critical Terms'), RULE(),
  P('The terms below are used in the analysis and are defined here in the sense in which this project uses them.'),
  SPACER(80),
  table(gw, [
    headerRow(['Term', 'Definition as used in this project'], gw, BROWN),
    gloss('Escapism', 'The habit of relieving dissatisfaction with one’s own life by mentally inhabiting another. Distinguished throughout this project from escape, which is temporary and returned from.', PALEBROWN),
    gloss('Intertextuality', 'The reading of one text through another, where neither influenced the other. The comparison is made by the reader, and its value lies in what the two works reveal about each other.', PALEPURPLE),
    gloss('Golden Age thinking', 'Paul’s phrase in Midnight in Paris for the belief that some earlier period was better than one’s own. The film treats it as a fallacy and demonstrates its infinite regress.', PALEBROWN),
    gloss('Restorative nostalgia', 'Svetlana Boym’s term for nostalgia that wants to rebuild and move into the lost past. Charley’s nostalgia is restorative: he tries to buy a ticket.', PALEPURPLE),
    gloss('Reflective nostalgia', 'Boym’s contrasting term for nostalgia that loves the past while knowing it cannot be re-entered. Gil arrives at reflective nostalgia in the final act; it is what allows him to stay.', PALEBROWN),
    gloss('Wish fulfilment', 'In psychoanalysis, the imaginative satisfaction of a desire that cannot be satisfied in reality. Sam’s diagnosis: “a waking-dream wish fulfillment”.', PALEPURPLE),
    gloss('First-day cover', 'An envelope bearing a stamp postmarked on its first day of issue, kept sealed by collectors. In the story it is the container of Sam’s letter and the proof of the third level.', PALEBROWN),
    gloss('Third place', 'Ray Oldenburg’s term for a social setting that is neither home nor work. Used in Part D to distinguish a healthy refuge from Charley’s attempt to make a refuge permanent.', PALEPURPLE),
  ]),
  BREAK(),
);

// ------------------------------------------------ BIBLIOGRAPHY (p30)
const REF = (text, italicPart) => new Paragraph({
  spacing: { after: 160, line: 300 },
  indent: { left: 400, hanging: 400 },
  alignment: AlignmentType.LEFT,
  children: text,
});

badd(
  H1('Bibliography and References'), RULE(),
  H2('Primary sources', { before: 0 }),
  REF([t('Finney, Jack. “The Third Level.” ', { size: 21, color: INK }), t('Vistas: Supplementary Reader in English for Class XII', { size: 21, italics: true, color: INK }), t('. New Delhi: National Council of Educational Research and Training. (Story first published 1950; collected in ', { size: 21, color: INK }), t('The Third Level', { size: 21, italics: true, color: INK }), t(', Rinehart, 1957.)', { size: 21, color: INK })]),
  REF([t('', { size: 21 }), t('Midnight in Paris', { size: 21, italics: true, color: INK }), t('. Directed by Woody Allen. Gravier Productions / Mediapro; distributed by Sony Pictures Classics, 2011. Feature film, 94 minutes.', { size: 21, color: INK })]),
  H2('Secondary reading'),
  REF([t('Boym, Svetlana. ', { size: 21, color: INK }), t('The Future of Nostalgia', { size: 21, italics: true, color: INK }), t('. New York: Basic Books, 2001. — for the distinction between restorative and reflective nostalgia used in Part B.5 and Appendix II.', { size: 21, color: INK })]),
  REF([t('Oldenburg, Ray. ', { size: 21, color: INK }), t('The Great Good Place', { size: 21, italics: true, color: INK }), t('. New York: Paragon House, 1989. — for the concept of the “third place”, adapted in Part D.', { size: 21, color: INK })]),
  REF([t('Finney, Jack. ', { size: 21, color: INK }), t('Time and Again', { size: 21, italics: true, color: INK }), t('. New York: Simon & Schuster, 1970. — consulted for context on Finney’s treatment of time travel without mechanism, referred to in Part A.1.', { size: 21, color: INK })]),
  H2('A note on quotation and originality'),
  P('All quotations from The Third Level are taken directly from the prescribed NCERT text and have been checked against it individually. Dialogue from Midnight in Paris is quoted as spoken in the film; where a scene is summarised rather than quoted, it is identified by location and action so that the reference can be verified.'),
  P('The six plates in this project — Plates I to VI, including the poster in Part C — are entirely original designs produced for this project. No photograph, film still, or promotional artwork has been reproduced. The colour palette throughout is restricted to blue, black, brown, purple and sky blue, in keeping with the project guidelines.'),
  SPACER(300),
  new Paragraph({ spacing: { after: 0 }, border: { top: { style: BorderStyle.SINGLE, size: 6, color: BROWN } }, children: [t('', { size: 2 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 200, after: 0 }, children: [t('Parts A to D, including all tables and plate commentary: approximately 4,000 words.', { size: 19, italics: true, color: MUTED })] }),
);

// ================================================================ DOC
const doc = new Document({
  creator: 'Class XII English Core Project',
  title: 'From Grand Central to the Silver Screen',
  description: 'CBSE Class XII English Core project on Jack Finney’s The Third Level and Midnight in Paris',
  sections: [
    {
      properties: {
        page: {
          size: { width: 11906, height: 16838 },
          margin: { top: 1134, bottom: 1134, left: 1701, right: 1134 },
        },
      },
      children: frontMatter,
    },
    {
      properties: {
        page: {
          size: { width: 11906, height: 16838 },
          margin: { top: 1134, bottom: 1247, left: 1701, right: 1134 },
          pageNumbers: { start: 1 },
        },
      },
      footers: {
        default: new Footer({
          children: [new Paragraph({
            alignment: AlignmentType.CENTER,
            spacing: { before: 200 },
            children: [
              t('From Grand Central to the Silver Screen   ·   ', { size: 16, color: MUTED }),
              new TextRun({ children: [PageNumber.CURRENT], font: BODY, size: 16, color: BROWN, bold: true }),
            ],
          })],
        }),
      },
      children: body,
    },
  ],
});

Packer.toBuffer(doc).then((buf) => {
  const out = process.argv[2] || path.join(__dirname, 'project.docx');
  fs.writeFileSync(out, buf);
  console.log('wrote', out, (buf.length / 1024).toFixed(0) + ' KB');
});
