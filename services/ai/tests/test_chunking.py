from app.chunking import chunk_pages
from app.extraction import PageText

def test_chunking_preserves_page_and_overlap():
    chunks=chunk_pages([PageText(3,"A"*200+". "+"B"*200)],chunk_chars=220,overlap_chars=20)
    assert len(chunks)>=2
    assert all(c.page==3 for c in chunks)
    assert [c.ordinal for c in chunks]==list(range(len(chunks)))

def test_invalid_overlap_rejected():
    try: chunk_pages([PageText(1,"text")],100,100)
    except ValueError: pass
    else: raise AssertionError("Expected ValueError")
