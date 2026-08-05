from __future__ import annotations

import re
from dataclasses import dataclass
from io import BytesIO

from docx import Document
from pypdf import PdfReader

from app.config import settings
from app.errors import PermanentProcessingError


@dataclass(frozen=True)
class PageText:
    page: int
    text: str


class ExtractionError(PermanentProcessingError):
    pass


def normalize(text: str) -> str:
    text = text.replace("\x00", " ")
    text = re.sub(r"[\t\r ]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def extract(data: bytes, content_type: str) -> list[PageText]:
    try:
        if content_type == "application/pdf":
            reader = PdfReader(BytesIO(data), strict=False)
            if len(reader.pages) > settings.max_pages:
                raise ExtractionError("Document exceeds page limit")
            pages = [
                PageText(index + 1, normalize(page.extract_text() or ""))
                for index, page in enumerate(reader.pages)
            ]
        elif content_type == "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
            document = Document(BytesIO(data))
            text = "\n".join(paragraph.text for paragraph in document.paragraphs)
            pages = [PageText(1, normalize(text))]
        elif content_type == "text/plain":
            pages = [PageText(1, normalize(data.decode("utf-8", errors="strict")))]
        else:
            raise ExtractionError("Unsupported content type")
    except ExtractionError:
        raise
    except (UnicodeDecodeError, ValueError, KeyError, OSError) as exc:
        raise ExtractionError("Document could not be safely extracted") from exc

    total = sum(len(page.text) for page in pages)
    if total == 0:
        raise ExtractionError("No extractable text")
    if total > settings.max_extracted_chars:
        raise ExtractionError("Extracted text exceeds safety limit")
    return pages
