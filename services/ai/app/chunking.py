from __future__ import annotations
from dataclasses import dataclass
from app.extraction import PageText

@dataclass(frozen=True)
class TextChunk:
    page: int
    ordinal: int
    text: str

def chunk_pages(pages:list[PageText],chunk_chars:int,overlap_chars:int)->list[TextChunk]:
    if chunk_chars<=0 or overlap_chars<0 or overlap_chars>=chunk_chars: raise ValueError("Invalid chunk configuration")
    chunks:list[TextChunk]=[]; ordinal=0
    for page in pages:
        text=page.text.strip(); start=0
        while start<len(text):
            end=min(len(text),start+chunk_chars)
            if end<len(text):
                boundary=max(text.rfind("\n",start,end),text.rfind(". ",start,end))
                if boundary>start+chunk_chars//2: end=boundary+1
            value=text[start:end].strip()
            if value: chunks.append(TextChunk(page.page,ordinal,value)); ordinal+=1
            if end>=len(text): break
            start=max(start+1,end-overlap_chars)
    return chunks
