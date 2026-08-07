from docx import Document

from build_thesis_docx import (
    DOCS,
    REFERENCE,
    add_centered,
    add_markdown,
    clear_reference_body,
    configure_page,
    configure_styles,
)


OUTPUT = DOCS / "export" / "LuxeStay_HuongDan_TraLoi_Rubric_DRAFT.docx"


def build() -> None:
    doc = Document(str(REFERENCE))
    clear_reference_body(doc)
    configure_styles(doc)
    configure_page(doc)
    doc.core_properties.title = "LuxeStay - Hướng dẫn trả lời rubric (DRAFT)"
    doc.core_properties.subject = "Rubric môn học và khóa luận tốt nghiệp"
    doc.core_properties.author = ""
    doc.core_properties.last_modified_by = ""

    add_centered(doc, "HƯỚNG DẪN TRẢ LỜI RUBRIC", 18, bold=True, after=10)
    add_centered(doc, "MÔN HỌC VÀ KHÓA LUẬN TỐT NGHIỆP", 16, bold=True, after=18)
    add_centered(doc, "HỆ THỐNG LUXESTAY", 14, bold=True, after=12)
    add_centered(doc, "Nguồn D03/D04 export ngày 28/07/2026 - BẢN DRAFT", 12, after=24)
    doc.add_page_break()

    replacements = []
    add_markdown(doc, DOCS / "RUBRIC_RESPONSE_GUIDE.md", replacements, skip_first_h1=True)
    doc.add_page_break()
    add_markdown(doc, DOCS / "thesis-assets" / "RUBRIC_MATRIX.md", replacements, skip_first_h1=True)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    doc.save(OUTPUT)
    print(f"Created {OUTPUT}")


if __name__ == "__main__":
    build()
