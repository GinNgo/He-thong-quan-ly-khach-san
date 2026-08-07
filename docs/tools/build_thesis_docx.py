from __future__ import annotations

import math
import re
from pathlib import Path

from PIL import Image
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.table import WD_ALIGN_VERTICAL
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Inches, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
REFERENCE = DOCS / "thesis-assets" / "templates" / "D07_Mau_5_TrinhBay_KLTN_2026-07-28.docx"
OUTPUT = DOCS / "export" / "LuxeStay_KhoaLuan_DRAFT.docx"

DIAGRAMS = DOCS / "thesis-assets" / "diagrams"
DIAGRAM_PNG = DIAGRAMS / "png"
SCREENSHOTS = DOCS / "screenshots"

MERMAID_DIAGRAMS = [
    DIAGRAMS / "uml-01.svg",
    None,  # Architecture block is kept as source text in THESIS.md.
    DIAGRAMS / "uml-04.svg",
    DIAGRAMS / "uml-08.svg",
    DIAGRAMS / "erd-03.svg",
    DIAGRAMS / "uml-16.svg",
    DIAGRAMS / "uml-11.svg",
    DIAGRAMS / "uml-18.svg",
]

SCREENSHOT_SET = [
    ("home-search-after-desktop.png", "Hình 4.A. Trang chủ và form tìm kiếm public - evidence CURRENT ngày 28/07/2026"),
    ("search-result-after.png", "Hình 4.B. Kết quả tìm kiếm - evidence HISTORICAL"),
    ("room-selection-after.png", "Hình 4.C. Chọn RoomType - evidence HISTORICAL"),
    ("admin-roles-after.png", "Hình 4.D. Quản lý vai trò - evidence HISTORICAL"),
    ("admin-rooms-after.png", "Hình 4.E. Quản lý phòng - evidence HISTORICAL"),
]


def set_run_font(run, name: str = "Times New Roman", size: float | None = None) -> None:
    run.font.name = name
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:ascii"), name)
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:hAnsi"), name)
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), name)
    if size is not None:
        run.font.size = Pt(size)


def style_font(style, size: float, *, bold: bool | None = None, italic: bool | None = None) -> None:
    style.font.name = "Times New Roman"
    style._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:ascii"), "Times New Roman")
    style._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:hAnsi"), "Times New Roman")
    style._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), "Times New Roman")
    style.font.size = Pt(size)
    style.font.bold = bold
    style.font.italic = italic


def configure_styles(doc: Document) -> None:
    normal = doc.styles["Normal"]
    style_font(normal, 13)
    normal.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    normal.paragraph_format.line_spacing = 1.5
    normal.paragraph_format.first_line_indent = Cm(1)
    normal.paragraph_format.space_after = Pt(6)

    h1 = doc.styles["Heading 1"]
    style_font(h1, 14, bold=True)
    h1.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    h1.paragraph_format.page_break_before = True
    h1.paragraph_format.keep_with_next = True
    h1.paragraph_format.space_before = Pt(0)
    h1.paragraph_format.space_after = Pt(12)
    h1.paragraph_format.line_spacing = 1.15

    h2 = doc.styles["Heading 2"]
    style_font(h2, 13, bold=True)
    h2.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    h2.paragraph_format.keep_with_next = True
    h2.paragraph_format.space_before = Pt(12)
    h2.paragraph_format.space_after = Pt(6)
    h2.paragraph_format.line_spacing = 1.15

    h3 = doc.styles["Heading 3"]
    style_font(h3, 13, bold=True)
    h3.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    h3.paragraph_format.keep_with_next = True
    h3.paragraph_format.space_before = Pt(10)
    h3.paragraph_format.space_after = Pt(5)

    h4 = doc.styles["Heading 4"]
    style_font(h4, 13, italic=True)
    h4.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    h4.paragraph_format.keep_with_next = True
    h4.paragraph_format.space_before = Pt(8)
    h4.paragraph_format.space_after = Pt(4)

    if "Caption" not in doc.styles:
        doc.styles.add_style("Caption", WD_STYLE_TYPE.PARAGRAPH)
    caption = doc.styles["Caption"]
    style_font(caption, 11, bold=True)
    caption.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    caption.paragraph_format.first_line_indent = Cm(0)
    caption.paragraph_format.space_before = Pt(3)
    caption.paragraph_format.space_after = Pt(8)
    caption.paragraph_format.keep_with_next = False

    for list_style_name in ("List Bullet", "List Number"):
        if list_style_name not in doc.styles:
            doc.styles.add_style(list_style_name, WD_STYLE_TYPE.PARAGRAPH)
        style = doc.styles[list_style_name]
        style_font(style, 13)
        style.paragraph_format.first_line_indent = None
        style.paragraph_format.space_after = Pt(3)
        style.paragraph_format.line_spacing = 1.5

    if "Front Matter Title" not in doc.styles:
        doc.styles.add_style("Front Matter Title", WD_STYLE_TYPE.PARAGRAPH)
    front = doc.styles["Front Matter Title"]
    style_font(front, 14, bold=True)
    front.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    front.paragraph_format.keep_with_next = True
    front.paragraph_format.space_after = Pt(18)

    if "Placeholder" not in doc.styles:
        doc.styles.add_style("Placeholder", WD_STYLE_TYPE.PARAGRAPH)
    placeholder = doc.styles["Placeholder"]
    style_font(placeholder, 11, italic=True)
    placeholder.font.color.rgb = RGBColor(0x66, 0x66, 0x66)
    placeholder.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    placeholder.paragraph_format.first_line_indent = Cm(0)
    placeholder.paragraph_format.line_spacing = 1.15

    if "Code Block" not in doc.styles:
        doc.styles.add_style("Code Block", WD_STYLE_TYPE.PARAGRAPH)
    code = doc.styles["Code Block"]
    code.font.name = "Consolas"
    code._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:ascii"), "Consolas")
    code._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:hAnsi"), "Consolas")
    code.font.size = Pt(9)
    code.paragraph_format.left_indent = Cm(0.5)
    code.paragraph_format.right_indent = Cm(0.5)
    code.paragraph_format.first_line_indent = Cm(0)
    code.paragraph_format.space_after = Pt(2)
    code.paragraph_format.line_spacing = 1.0

    if "Table Grid" not in doc.styles:
        doc.styles.add_style("Table Grid", WD_STYLE_TYPE.TABLE)


def clear_reference_body(doc: Document) -> None:
    body = doc._element.body
    for child in list(body):
        if child.tag != qn("w:sectPr"):
            body.remove(child)


def clear_story(story) -> None:
    element = story._element
    for child in list(element):
        element.remove(child)
    element.append(OxmlElement("w:p"))


def add_page_field(paragraph) -> None:
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = " PAGE "
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    text = OxmlElement("w:t")
    text.text = "1"
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.extend([begin, instr, separate, text, end])
    set_run_font(run, size=11)


def configure_page(doc: Document) -> None:
    for section in doc.sections:
        section.page_width = Cm(21)
        section.page_height = Cm(29.7)
        section.left_margin = Cm(3)
        section.right_margin = Cm(2)
        section.top_margin = Cm(2)
        section.bottom_margin = Cm(2)
        section.header_distance = Cm(1)
        section.footer_distance = Cm(1)
        section.different_first_page_header_footer = False
        clear_story(section.header)
        clear_story(section.footer)
        footer_p = section.footer.paragraphs[0]
        footer_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        add_page_field(footer_p)

    settings = doc.settings._element
    for node in settings.findall(qn("w:updateFields")):
        settings.remove(node)
    update = OxmlElement("w:updateFields")
    update.set(qn("w:val"), "true")
    settings.append(update)


def add_centered(doc: Document, text: str, size: float, *, bold: bool = False, before: float = 0, after: float = 0):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(before)
    p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.15
    run = p.add_run(text)
    set_run_font(run, size=size)
    run.bold = bold
    return p


def add_cover(doc: Document) -> None:
    add_centered(doc, "TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP.HCM", 14, bold=True)
    add_centered(doc, "KHOA CÔNG NGHỆ THÔNG TIN", 14, bold=True)
    add_centered(doc, "BỘ MÔN <ĐIỀN TÊN BỘ MÔN>", 13, bold=True, after=24)
    add_centered(doc, "<LOGO KHOA CNTT>", 12, after=24)
    add_centered(doc, "<HỌ VÀ TÊN SINH VIÊN> - <MÃ SỐ SINH VIÊN>", 13, bold=True, after=18)
    add_centered(doc, "ĐỀ TÀI", 13, bold=True)
    add_centered(doc, "HỆ THỐNG QUẢN LÝ KHÁCH SẠN VÀ ĐẶT PHÒNG TRỰC TUYẾN LUXESTAY", 16, bold=True, before=6, after=20)
    add_centered(doc, "KHÓA LUẬN TỐT NGHIỆP KỸ SƯ CÔNG NGHỆ THÔNG TIN", 14, bold=True, after=28)
    add_centered(doc, "GIẢNG VIÊN HƯỚNG DẪN", 13, bold=True)
    add_centered(doc, "<HỌ VÀ TÊN GIẢNG VIÊN HƯỚNG DẪN>", 13, bold=True, after=36)
    add_centered(doc, "KHÓA <xxxx - yyyy>", 13, bold=True)
    add_centered(doc, "TP. HỒ CHÍ MINH, 2026", 13, bold=True, before=12)
    doc.add_page_break()


def add_placeholder_slot(doc: Document, title: str, message: str) -> None:
    p = doc.add_paragraph(title, style="Front Matter Title")
    p.paragraph_format.page_break_before = True
    note = doc.add_paragraph(message, style="Placeholder")
    note.paragraph_format.space_before = Pt(24)
    doc.add_page_break()


def clean_inline(text: str) -> str:
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    text = text.replace("**", "")
    text = re.sub(r"(?<!\*)\*(?!\*)", "", text)
    return text.strip()


def add_inline_runs(paragraph, text: str) -> None:
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    parts = re.split(r"(`[^`]+`|\*\*[^*]+\*\*)", text)
    for part in parts:
        if not part:
            continue
        if part.startswith("`") and part.endswith("`"):
            run = paragraph.add_run(part[1:-1])
            set_run_font(run, "Consolas", 10.5)
        elif part.startswith("**") and part.endswith("**"):
            run = paragraph.add_run(part[2:-2])
            set_run_font(run, size=13)
            run.bold = True
        else:
            run = paragraph.add_run(part.replace("*", ""))
            set_run_font(run, size=13)


def add_body_paragraph(doc: Document, text: str, style: str = "Normal"):
    p = doc.add_paragraph(style=style)
    add_inline_runs(p, text)
    if style in ("List Bullet", "List Number"):
        p.paragraph_format.first_line_indent = None
    return p


def set_cell_margins(cell, top=80, start=120, bottom=80, end=120) -> None:
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for margin, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tc_mar.find(qn(f"w:{margin}"))
        if node is None:
            node = OxmlElement(f"w:{margin}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def repeat_header(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    header = OxmlElement("w:tblHeader")
    header.set(qn("w:val"), "true")
    tr_pr.append(header)


def add_markdown_table(doc: Document, rows: list[list[str]]) -> None:
    if not rows:
        return
    col_count = max(len(row) for row in rows)
    normalized = [row + [""] * (col_count - len(row)) for row in rows]
    table = doc.add_table(rows=1, cols=col_count)
    table.style = "Table Grid"
    table.autofit = False
    tbl_pr = table._tbl.tblPr
    borders = tbl_pr.first_child_found_in("w:tblBorders")
    if borders is None:
        borders = OxmlElement("w:tblBorders")
        tbl_pr.append(borders)
    for edge in ("top", "start", "bottom", "end", "insideH", "insideV"):
        node = OxmlElement(f"w:{edge}")
        node.set(qn("w:val"), "single")
        node.set(qn("w:sz"), "4")
        node.set(qn("w:space"), "0")
        node.set(qn("w:color"), "A6A6A6")
        borders.append(node)

    max_lengths = [max(4, max(len(clean_inline(row[col])) for row in normalized)) for col in range(col_count)]
    total = sum(max_lengths)
    widths = [Cm(16 * length / total) for length in max_lengths]

    for row_index, source_row in enumerate(normalized):
        row = table.rows[0] if row_index == 0 else table.add_row()
        if row_index == 0:
            repeat_header(row)
        for col_index, value in enumerate(source_row):
            cell = row.cells[col_index]
            cell.width = widths[col_index]
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            set_cell_margins(cell)
            p = cell.paragraphs[0]
            p.paragraph_format.first_line_indent = Cm(0)
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.line_spacing = 1.15
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER if len(value) < 24 else WD_ALIGN_PARAGRAPH.LEFT
            run = p.add_run(clean_inline(value))
            set_run_font(run, size=12)
            run.bold = row_index == 0
            if row_index == 0:
                tc_pr = cell._tc.get_or_add_tcPr()
                shading = OxmlElement("w:shd")
                shading.set(qn("w:fill"), "EDEDED")
                tc_pr.append(shading)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


def svg_viewbox(path: Path) -> tuple[float, float]:
    head = path.read_text(encoding="utf-8", errors="ignore")[:2000]
    match = re.search(r'viewBox="[\d.+-]+\s+[\d.+-]+\s+([\d.+-]+)\s+([\d.+-]+)"', head)
    if not match:
        return 1000.0, 700.0
    return float(match.group(1)), float(match.group(2))


def image_variants(path: Path, output_dir: Path) -> list[tuple[Path, str | None]]:
    """Split very tall assets into two overlapping panels so Word can paginate them."""
    with Image.open(path) as image:
        width, height = image.size
        if height / max(width, 1) <= 1.35:
            return [(path, None)]
        output_dir.mkdir(parents=True, exist_ok=True)
        midpoint = height // 2
        overlap = min(120, max(24, height // 100))
        specs = [
            ("a", (0, 0, width, min(height, midpoint + overlap))),
            ("b", (0, max(0, midpoint - overlap), width, height)),
        ]
        variants: list[tuple[Path, str | None]] = []
        for label, box in specs:
            target = output_dir / f"{path.stem}-{label}.png"
            if not target.exists():
                image.crop(box).save(target, format="PNG")
            variants.append((target, label))
        return variants


def add_diagram_image(doc: Document, svg_path: Path, assets: list[Path]) -> None:
    """Embed the raster asset; SVG remains a review/source artifact outside Word."""
    png_path = DIAGRAM_PNG / f"{svg_path.stem}.png"
    if not png_path.exists():
        raise FileNotFoundError(
            f"Missing Word-compatible diagram PNG: {png_path}. "
            "Run node docs/tools/render_diagrams_png.js first."
        )
    width_units, height_units = svg_viewbox(svg_path)
    max_width, max_height = 6.15, 8.25
    width = min(max_width, max_height * width_units / height_units)
    height = width * height_units / width_units
    if height > max_height:
        height = max_height
        width = height * width_units / height_units

    variants = image_variants(png_path, DIAGRAM_PNG / "panels")
    for image_path, panel in variants:
        with Image.open(image_path) as panel_image:
            panel_ratio = panel_image.height / max(panel_image.width, 1)
        panel_width = max_width if panel else width
        panel_height = panel_width * panel_ratio if panel else height
        if panel_height > max_height:
            panel_height = max_height
            panel_width = panel_height / panel_ratio
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.first_line_indent = Cm(0)
        shape = p.add_run().add_picture(str(image_path), width=Inches(panel_width), height=Inches(panel_height))
        suffix = f" (phần {panel})" if panel else ""
        shape._inline.docPr.set("descr", f"Sơ đồ {svg_path.stem.upper()}{suffix} được raster từ {svg_path.name}")
        shape._inline.docPr.set("title", f"{image_path.name}")
        assets.append(image_path)
        if panel:
            doc.add_paragraph(f"{svg_path.stem.upper()} - phần ({panel})", style="Caption")


def add_screenshots(doc: Document) -> None:
    for filename, caption in SCREENSHOT_SET:
        image_path = SCREENSHOTS / filename
        if not image_path.exists():
            continue
        variants = image_variants(image_path, SCREENSHOTS / "docx-panels")
        for image_variant, panel in variants:
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.first_line_indent = Cm(0)
            with Image.open(image_variant) as image:
                image_width, image_height = image.size
            display_width = 6.0
            display_height = display_width * image_height / image_width
            if display_height > 8.25:
                display_height = 8.25
                display_width = display_height * image_width / image_height
            shape = p.add_run().add_picture(str(image_variant), width=Inches(display_width), height=Inches(display_height))
            panel_suffix = f" ({panel})" if panel else ""
            # Keep screenshot evidence accessible when the DOCX is reviewed with assistive tools.
            shape._inline.docPr.set("descr", f"{caption}{panel_suffix}")
            shape._inline.docPr.set("title", image_variant.name)
            doc.add_paragraph(f"{caption}{panel_suffix}", style="Caption")
    doc.add_paragraph(
        "Các ảnh trên là evidence lịch sử và phải được thay bằng screenshot CURRENT trước bản REVIEW/FINAL.",
        style="Placeholder",
    )


def add_mermaid_block(doc: Document, code_lines: list[str], block_index: int, assets) -> None:
    diagram_path = MERMAID_DIAGRAMS[block_index] if block_index < len(MERMAID_DIAGRAMS) else None
    if diagram_path and diagram_path.exists():
        add_diagram_image(doc, diagram_path, assets)
        return
    doc.add_paragraph(
        "[Sơ đồ Mermaid kiến trúc giữ ở source Markdown; cần render bổ sung trong vòng REVIEW.]",
        style="Placeholder",
    )
    for code_line in code_lines:
        p = doc.add_paragraph(style="Code Block")
        p.add_run(code_line)


def add_markdown(doc: Document, path: Path, assets, *, skip_first_h1: bool = False) -> None:
    lines = path.read_text(encoding="utf-8").splitlines()
    i = 0
    mermaid_index = 0
    first_h1_skipped = False
    while i < len(lines):
        raw = lines[i]
        line = raw.rstrip()
        stripped = line.strip()
        if not stripped:
            i += 1
            continue

        if stripped.startswith("```"):
            language = stripped[3:].strip().lower()
            code_lines: list[str] = []
            i += 1
            while i < len(lines) and not lines[i].strip().startswith("```"):
                code_lines.append(lines[i])
                i += 1
            if language == "mermaid":
                add_mermaid_block(doc, code_lines, mermaid_index, assets)
                mermaid_index += 1
            else:
                for code_line in code_lines:
                    p = doc.add_paragraph(style="Code Block")
                    p.add_run(code_line)
            i += 1
            continue

        if stripped.startswith("|") and i + 1 < len(lines) and re.match(r"^\s*\|?\s*:?-+", lines[i + 1]):
            rows: list[list[str]] = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                cells = [cell.strip() for cell in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-+:?", cell.replace(" ", "")) for cell in cells):
                    rows.append(cells)
                i += 1
            add_markdown_table(doc, rows)
            continue

        heading = re.match(r"^(#{1,4})\s+(.+)$", stripped)
        if heading:
            level = len(heading.group(1))
            text = clean_inline(heading.group(2))
            if skip_first_h1 and level == 1 and not first_h1_skipped:
                first_h1_skipped = True
                i += 1
                continue
            if level == 1 and text.upper().startswith("CHƯƠNG") and i + 1 < len(lines):
                next_heading = re.match(r"^#\s+(.+)$", lines[i + 1].strip())
                if next_heading:
                    text = f"{text}\n{clean_inline(next_heading.group(1))}"
                    i += 1
            p = doc.add_paragraph(style=f"Heading {level}")
            p.paragraph_format.first_line_indent = Cm(0)
            for part_index, part in enumerate(text.split("\n")):
                run = p.add_run(part)
                set_run_font(run, size=14 if level == 1 else 13)
                run.bold = level <= 3
                if part_index < len(text.split("\n")) - 1:
                    run.add_break()
            i += 1
            continue

        if re.match(r"^[-*]\s+", stripped):
            add_body_paragraph(doc, re.sub(r"^[-*]\s+", "", stripped), "List Bullet")
            i += 1
            continue

        if re.match(r"^\d+\.\s+", stripped):
            add_body_paragraph(doc, re.sub(r"^\d+\.\s+", "", stripped), "List Number")
            i += 1
            continue

        if stripped.startswith(">"):
            p = add_body_paragraph(doc, stripped.lstrip("> "))
            for run in p.runs:
                run.italic = True
            i += 1
            continue

        if re.match(r"^(Hình|Bảng)\s+\d", stripped):
            doc.add_paragraph(clean_inline(stripped), style="Caption")
            i += 1
            continue

        add_body_paragraph(doc, stripped)
        if stripped.startswith("Ảnh minh họa hiện có trong `docs/screenshots/`"):
            add_screenshots(doc)
        i += 1


def add_front_matter(doc: Document, title: str, path: Path, assets) -> None:
    p = doc.add_paragraph(title, style="Front Matter Title")
    p.paragraph_format.page_break_before = True
    add_markdown(doc, path, assets, skip_first_h1=True)
    doc.add_page_break()


def add_toc(doc: Document) -> None:
    p = doc.add_paragraph("MỤC LỤC", style="Front Matter Title")
    p.paragraph_format.page_break_before = True
    toc_p = doc.add_paragraph()
    toc_p.paragraph_format.first_line_indent = Cm(0)
    field = OxmlElement("w:fldSimple")
    field.set(qn("w:instr"), 'TOC \\o "1-4" \\h \\z \\u')
    run = OxmlElement("w:r")
    text = OxmlElement("w:t")
    text.text = "Mở file trong Word và chọn Update Field để sinh mục lục."
    run.append(text)
    field.append(run)
    toc_p._p.append(field)
    doc.add_paragraph("Bản DRAFT đặt `w:updateFields=true`; số trang sẽ cập nhật khi mở bằng Microsoft Word.", style="Placeholder")
    doc.add_page_break()


def figure_caption(svg_path: Path) -> str:
    """Reuse the human-readable caption from the maintained UML/ERD source."""
    source = DOCS / ("UML.md" if svg_path.stem.startswith("uml-") else "ERD.md")
    token = svg_path.stem.upper()
    pattern = re.compile(rf"(?:\*\*)?Hình\s+{re.escape(token)}[.:]\s*(.*?)(?:\*\*)?$", re.IGNORECASE)
    for line in source.read_text(encoding="utf-8").splitlines():
        match = pattern.match(line.strip())
        if match:
            return f"Hình {token}. {clean_inline(match.group(1))}"
    return f"Hình {token}. Sơ đồ {token}"


def add_full_diagram_appendix(doc: Document, assets: list[Path]) -> None:
    """Embed every maintained UML/ERD source so the report visibly covers all diagrams."""
    doc.add_paragraph("PHỤ LỤC C. BỘ SƠ ĐỒ UML/ERD ĐẦY ĐỦ", style="Heading 2")
    add_body_paragraph(
        doc,
        "Phụ lục này nhúng toàn bộ sơ đồ đã render từ docs/UML.md và docs/ERD.md. Các capability DEFERRED được giữ nhãn trong caption và không được diễn giải là chức năng đã hoàn thành.",
    )
    for svg_path in sorted(DIAGRAMS.glob("*.svg")):
        add_diagram_image(doc, svg_path, assets)
        doc.add_paragraph(figure_caption(svg_path), style="Caption")
        add_body_paragraph(doc, f"Nguồn source: docs/thesis-assets/diagrams/{svg_path.name}; asset Word: docs/thesis-assets/diagrams/png/{svg_path.stem}.png.", "Placeholder")


def build() -> None:
    if not REFERENCE.exists():
        raise FileNotFoundError(f"Missing official D07 reference: {REFERENCE}")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)

    doc = Document(str(REFERENCE))
    clear_reference_body(doc)
    configure_styles(doc)
    configure_page(doc)
    doc.core_properties.title = "LuxeStay - Khóa luận tốt nghiệp (DRAFT)"
    doc.core_properties.subject = "Hệ thống quản lý khách sạn và đặt phòng trực tuyến"
    doc.core_properties.author = ""
    doc.core_properties.last_modified_by = ""

    assets: list[Path] = []
    add_cover(doc)
    add_placeholder_slot(
        doc,
        "BIÊN BẢN CHẤM HOẶC BẢNG ĐIỂM CHẤM KHÓA LUẬN TỐT NGHIỆP",
        "DRAFT/BLOCKED - thay bằng bảng điểm hoặc biên bản chính thức sau khi hội đồng chấm.",
    )
    add_placeholder_slot(
        doc,
        "PHIẾU NHẬN XÉT KHÓA LUẬN TỐT NGHIỆP - GIẢNG VIÊN PHẢN BIỆN",
        "DRAFT/NEEDS_EVIDENCE - dùng mẫu D08; bổ sung thông tin, nhận xét và chữ ký chính thức.",
    )
    add_placeholder_slot(
        doc,
        "BIÊN BẢN CHỈNH SỬA KHÓA LUẬN TỐT NGHIỆP",
        "OPTIONAL/DEFERRED - chỉ giữ slot này khi hội đồng yêu cầu chỉnh sửa.",
    )
    add_placeholder_slot(
        doc,
        "PHIẾU NHẬN XÉT KHÓA LUẬN TỐT NGHIỆP - GIẢNG VIÊN HƯỚNG DẪN",
        "DRAFT/NEEDS_EVIDENCE - dùng mẫu D08; bổ sung thông tin, nhận xét và chữ ký chính thức.",
    )

    add_front_matter(doc, "LỜI CẢM ƠN", DOCS / "thesis-assets" / "front-matter" / "LOI_CAM_ON.md", assets)
    add_front_matter(doc, "LỜI CAM ĐOAN", DOCS / "thesis-assets" / "front-matter" / "LOI_CAM_DOAN.md", assets)
    add_front_matter(doc, "TÓM TẮT", DOCS / "thesis-assets" / "front-matter" / "TOM_TAT.md", assets)
    add_toc(doc)

    add_markdown(doc, DOCS / "THESIS.md", assets)

    appendix = doc.add_paragraph("PHỤ LỤC", style="Heading 1")
    appendix.paragraph_format.first_line_indent = Cm(0)
    add_body_paragraph(
        doc,
        "Phụ lục điện tử gồm ma trận rubric, registry bằng chứng, API/UML/ERD chi tiết và source SVG trong thư mục docs/. Bản FINAL chỉ đưa các phụ lục được giảng viên hướng dẫn duyệt.",
    )
    doc.add_paragraph("PHỤ LỤC A. DANH MỤC SƠ ĐỒ NGUỒN", style="Heading 2")
    for svg_path in sorted(DIAGRAMS.glob("*.svg")):
        add_body_paragraph(doc, f"{svg_path.stem.upper()}: docs/thesis-assets/diagrams/{svg_path.name}", "List Bullet")
    doc.add_paragraph("PHỤ LỤC B. MA TRẬN RUBRIC", style="Heading 2")
    add_body_paragraph(
        doc,
        "D03 và D04 đã được mapping 14/14 tiêu chí. Bảng chi tiết nằm tại docs/thesis-assets/RUBRIC_MATRIX.md và hướng dẫn trả lời tại docs/RUBRIC_RESPONSE_GUIDE.md.",
    )
    add_full_diagram_appendix(doc, assets)

    doc.save(OUTPUT)
    print(f"Created {OUTPUT}")
    print(f"Embedded PNG diagrams: {len(assets)}")


if __name__ == "__main__":
    build()
