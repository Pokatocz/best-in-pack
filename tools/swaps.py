"""Whole-word renames applied to the shared Java source after preprocessing (like Stonecutter swaps).
Each entry: (condition, regex, replacement[, file regex]). Keep these for pure renames; real behaviour changes use //#if.
A swap with a file regex only applies to files whose path matches it."""
LEGACY = "com.bestinpack.client.gui.legacy"
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

    # ---- 1.18 – 1.19.4 -------------------------------------------------------------------------------------------
    # Before 1.20 there is no GuiGraphics (screens draw through a PoseStack): the mod's own class of the same name,
    # with the handful of methods the mod calls, takes its place.
    ("MC < 1.20", r"\bnet\.minecraft\.client\.gui\.GuiGraphics\b", LEGACY + ".GuiGraphics"),
    ("MC < 1.20", r"\bthis\.renderBackground\(context\)", "this.renderBackground(context.pose())"),
    ("MC < 1.20", r"\bsuper\.render\(context,", "super.render(context.pose(),"),
    # The focus rework of 1.19.4 made setFocused public; before, an edit box had its own setFocus.
    ("MC < 1.19.4", r"\bsearchField\.setFocused\(", "searchField.setFocus("),
    # Infinite effect durations came with 1.19.4.
    ("MC < 1.19.4", r"\binst\.isInfiniteDuration\(\)", "false"),
    # Before 1.19.3 widgets had public x/y fields, a constructor instead of a builder and no tooltip objects, and an
    # edit box had no hint: same-named subclasses with those methods stand in (only in the screens that build widgets).
    ("MC < 1.19.3", r"^import net\.minecraft\.client\.gui\.components\.(Button|EditBox|ImageButton|Tooltip);",
     r"import " + LEGACY + r".\1;", r"/BestInPackScreen\.java$"),
    ("MC < 1.19.3", r"^import net\.minecraft\.client\.gui\.components\.(Button|Tooltip);",
     r"import " + LEGACY + r".\1;", r"/BestInPackClient\.java$"),
    ("MC < 1.19.3", r"\bimg\.getX\(\)", "img.x", r"/BestInPackClient\.java$"),
    # Registries: BuiltInRegistries.X was Registry.X, Registries.X was Registry.X_REGISTRY.
    ("MC < 1.19.3", r"^import net\.minecraft\.core\.registries\.(BuiltInRegistries|Registries);", ""),
    ("MC < 1.19.3", r"\b(net\.minecraft\.core\.registries\.)?BuiltInRegistries\.", "net.minecraft.core.Registry."),
    ("MC < 1.19.3", r"\bRegistries\.ITEM\b", "net.minecraft.core.Registry.ITEM_REGISTRY"),
    ("MC < 1.19.3", r"\bTooltipFlag\.NORMAL\b", "TooltipFlag.Default.NORMAL"),
    # Attributes are kept as holders (they are holders in the game from 1.20.5 on); the registry only hands out
    # holders for any value from 1.19.3 on, and 1.18 – 1.18.1 has no Holder at all: a small stand-in there.
    ("MC < 1.19.3", r"^import net\.minecraft\.core\.Holder;", "import com.bestinpack.index.legacy.Holder;", r"/ItemIndex\.java$"),
    ("MC < 1.19.3", r"^import net\.minecraft\.core\.HolderSet;", "", r"/ItemIndex\.java$"),
    # The self-test reads widget positions and focuses EMI's search box.
    ("MC < 1.19.3", r"\b(w|bip)\.getX\(\)", r"\1.x", r"/SmokeTest\.java$"),
    ("MC < 1.19.3", r"\b(w|bip)\.getY\(\)", r"\1.y", r"/SmokeTest\.java$"),
    ("MC < 1.19.4", r"\bsearch\)\.setFocused\(", "search).setFocus(", r"/SmokeTest\.java$"),
    # Forge 38 – 40 (1.18.x): the events had longer names.
    ("MC < 1.19", r"\bClientPlayerNetworkEvent\.LoggingIn\b", "ClientPlayerNetworkEvent.LoggedInEvent", r"/BestInPackForge\.java$"),
    ("MC < 1.19", r"\bClientPlayerNetworkEvent\.LoggingOut\b", "ClientPlayerNetworkEvent.LoggedOutEvent", r"/BestInPackForge\.java$"),
    ("MC < 1.19", r"\bScreenEvent\.KeyPressed\.Pre\b", "ScreenEvent.KeyboardKeyPressedEvent.Pre", r"/BestInPackForge\.java$"),
    ("MC < 1.19", r"\bScreenEvent\.Init\.Post\b", "ScreenEvent.InitScreenEvent.Post", r"/BestInPackForge\.java$"),
    ("MC < 1.19", r"\bScreenEvent\.Render\.Pre\b", "ScreenEvent.DrawScreenEvent.Pre", r"/BestInPackForge\.java$"),
    # Text: Component.literal/translatable/empty came with 1.19.
    ("MC < 1.19", r"\b(net\.minecraft\.network\.chat\.)?Component\.literal\(", "new net.minecraft.network.chat.TextComponent("),
    ("MC < 1.19", r"\b(net\.minecraft\.network\.chat\.)?Component\.translatable\(", "new net.minecraft.network.chat.TranslatableComponent("),
    ("MC < 1.19", r"\b(net\.minecraft\.network\.chat\.)?Component\.empty\(\)", 'new net.minecraft.network.chat.TextComponent("")'),
]
