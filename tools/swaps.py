"""Whole-word renames applied to the shared Java source after preprocessing (like Stonecutter swaps).
Each entry: (condition, regex, replacement). Keep these for pure renames; real behaviour changes use //#if."""
SWAPS = [
    # 26.1: GuiGraphics became GuiGraphicsExtractor and Screen.render became extractRenderState.
    ("MC >= 26.1", r"\bGuiGraphics\b", "GuiGraphicsExtractor"),
    ("MC >= 26.1", r"\bpublic void render\(GuiGraphicsExtractor\b", "public void extractRenderState(GuiGraphicsExtractor"),
    ("MC >= 26.1", r"\bsuper\.render\(", "super.extractRenderState("),
    ("MC >= 26.1", r"\bparent\.render\(", "parent.extractRenderState("),
    ("MC >= 26.1", r"\bcontext\.drawString\(", "context.text("),
    ("MC >= 26.1", r"\bcontext\.drawCenteredString\(", "context.centeredText("),
    ("MC >= 26.1", r"\bcontext\.renderItem\(", "context.item("),
    ("MC >= 26.1", r"\bcontext\.renderItemDecorations\(", "context.itemDecorations("),
]
