#!/usr/bin/env python3
"""
Inject Search UI extension translation keys (search_category / display_mode / favorites
prompts / new SearchConfig entries) into every assets/omnichest/lang/*.json file.

Idempotent: skips keys that already exist. Safe to re-run.
"""
import json
import os
import sys
from pathlib import Path

LANG_DIR = Path(__file__).resolve().parent.parent / "fabric" / "src" / "client" / "resources" / "assets" / "omnichest" / "lang"

# Map: locale code -> dict of translations to add. en_us is already done; others below.
T = {}

# Generic helper to build per-locale dict from a fixed key order
ORDER = [
    "config.omnichest.search.enableCategoryTabs",
    "config.omnichest.search.enableCategoryTabs.tooltip",
    "config.omnichest.search.enableFavorites",
    "config.omnichest.search.enableFavorites.tooltip",
    "config.omnichest.search.favoriteHighlight",
    "config.omnichest.search.favoriteHighlight.tooltip",
    "config.omnichest.search.rememberLastDisplayMode",
    "config.omnichest.search.rememberLastDisplayMode.tooltip",
    "config.omnichest.search.compactTabMode",
    "config.omnichest.search.compactTabMode.tooltip",
    "config.omnichest.search.defaultDisplayMode",
    "config.omnichest.search.defaultDisplayMode.tooltip",
    "omnichest.search_category.all",
    "omnichest.search_category.favorites",
    "omnichest.search_category.building",
    "omnichest.search_category.wood",
    "omnichest.search_category.stone",
    "omnichest.search_category.ore",
    "omnichest.search_category.redstone",
    "omnichest.search_category.farming",
    "omnichest.search_category.food",
    "omnichest.search_category.combat",
    "omnichest.search_category.tool",
    "omnichest.search_category.enchant",
    "omnichest.search_category.potion",
    "omnichest.search_category.decoration",
    "omnichest.search_category.nether",
    "omnichest.search_category.end",
    "omnichest.search_category.misc",
    "omnichest.search.display_mode.label",
    "omnichest.search.display_mode.compact_grid",
    "omnichest.search.display_mode.large_grid",
    "omnichest.search.display_mode.list",
    "omnichest.search.display_mode.detailed",
    "omnichest.search.display_mode.icon_only",
    "omnichest.search.favorites.add_tooltip",
    "omnichest.search.favorites.remove_tooltip",
    "omnichest.search.favorites.empty",
]

def pack(values):
    assert len(values) == len(ORDER), f"len mismatch: {len(values)} vs {len(ORDER)}"
    return dict(zip(ORDER, values))

# --- Translations ---------------------------------------------------------
# ja_jp
T["ja_jp"] = pack([
    "カテゴリタブを有効化",
    "検索画面の上部に Creative Inventory 風のタブを表示します。",
    "お気に入りを有効化",
    "アイテムに★を付けて先頭に並べたり、お気に入りタブで絞り込めます。",
    "お気に入りハイライト",
    "お気に入り登録したアイテムに金色の発光エフェクトを追加します。",
    "前回の表示モードを記憶",
    "ON にすると次回起動時も前回選んだ表示モードを再現します。",
    "コンパクトタブ表示",
    "選択中タブもアイコンのみで表示します（名前ラベルなし）。",
    "デフォルト表示モード",
    "検索画面を開いた時の初期表示モード。",
    "すべて", "お気に入り",
    "建築", "木材", "石材", "鉱石", "レッドストーン", "農業", "食料", "戦闘", "ツール",
    "エンチャント", "ポーション", "装飾", "ネザー", "エンド", "その他",
    "表示モード", "コンパクト", "大アイコン", "リスト", "詳細", "アイコンのみ",
    "右クリックまたは Alt+クリックでお気に入り登録",
    "右クリックまたは Alt+クリックでお気に入り解除",
    "お気に入り未登録。アイテムを右クリックで★が付けられます。",
])

# ko_kr
T["ko_kr"] = pack([
    "카테고리 탭 활성화",
    "검색 화면 상단에 크리에이티브 인벤토리 스타일의 탭을 표시합니다.",
    "즐겨찾기 활성화",
    "아이템에 ★을 달아 상단에 정렬하거나 즐겨찾기 탭에서 필터링합니다.",
    "즐겨찾기 강조 표시",
    "즐겨찾기 항목에 황금색 발광 효과를 추가합니다.",
    "마지막 표시 모드 기억",
    "켜면 다음 실행 시 이전에 선택한 표시 모드를 다시 사용합니다.",
    "컴팩트 탭 모드",
    "선택된 탭도 아이콘만 표시합니다 (라벨 없음).",
    "기본 표시 모드",
    "검색 화면을 열 때의 초기 표시 모드.",
    "전체", "즐겨찾기",
    "건축", "목재", "석재", "광석", "레드스톤", "농업", "음식", "전투", "도구",
    "마법부여", "물약", "장식", "네더", "엔드", "기타",
    "표시 모드", "컴팩트 그리드", "큰 아이콘", "리스트", "자세히", "아이콘만",
    "오른쪽 클릭 또는 Alt+클릭으로 즐겨찾기 추가",
    "오른쪽 클릭 또는 Alt+클릭으로 즐겨찾기 해제",
    "즐겨찾기가 없습니다. 아이템을 오른쪽 클릭하여 추가하세요.",
])

# zh_cn
T["zh_cn"] = pack([
    "启用分类标签",
    "在搜索界面顶部显示创造模式背包风格的分类标签。",
    "启用收藏",
    "为物品加★，使其排在顶部，或在收藏标签中筛选。",
    "收藏高亮",
    "为收藏的物品添加金色发光效果。",
    "记住上次显示模式",
    "开启后，下次打开时会恢复上次选择的显示模式。",
    "紧凑标签模式",
    "即使是当前选中的标签也只显示图标（不显示文字标签）。",
    "默认显示模式",
    "打开搜索界面时使用的初始显示模式。",
    "全部", "收藏",
    "建筑", "木材", "石材", "矿物", "红石", "农业", "食物", "战斗", "工具",
    "附魔", "药水", "装饰", "下界", "末地", "其他",
    "显示模式", "紧凑网格", "大图标", "列表", "详细", "仅图标",
    "右键单击或按 Alt+单击添加到收藏",
    "右键单击或按 Alt+单击从收藏中移除",
    "暂无收藏。右键单击物品即可加★。",
])

# zh_tw
T["zh_tw"] = pack([
    "啟用分類標籤",
    "在搜尋畫面頂部顯示創造模式物品欄風格的分類標籤。",
    "啟用收藏",
    "為物品加★以排在頂部，或於收藏標籤中篩選。",
    "收藏高亮",
    "為收藏的物品加上金色發光特效。",
    "記住上次顯示模式",
    "開啟後下次開啟時會還原前次選擇的顯示模式。",
    "緊湊標籤模式",
    "即使目前選中的標籤也僅顯示圖示（不顯示文字標籤）。",
    "預設顯示模式",
    "開啟搜尋畫面時使用的初始顯示模式。",
    "全部", "收藏",
    "建築", "木材", "石材", "礦物", "紅石", "農業", "食物", "戰鬥", "工具",
    "附魔", "藥水", "裝飾", "地獄", "終界", "其他",
    "顯示模式", "緊湊網格", "大圖示", "清單", "詳細", "僅圖示",
    "右鍵點擊或 Alt+點擊以加入收藏",
    "右鍵點擊或 Alt+點擊以移除收藏",
    "尚無收藏。右鍵點擊物品即可加★。",
])

# de_de
T["de_de"] = pack([
    "Kategorie-Tabs aktivieren",
    "Zeigt im Suchfenster Kategorie-Tabs im Stil des Kreativinventars.",
    "Favoriten aktivieren",
    "Markiere Items mit ★, um sie ganz oben zu sortieren oder im Favoriten-Tab zu filtern.",
    "Favoriten hervorheben",
    "Gibt favorisierten Items einen goldenen Glüheffekt in der Ergebnisliste.",
    "Letzten Anzeigemodus merken",
    "Wenn aktiv, wird der zuletzt gewählte Anzeigemodus beim nächsten Öffnen wiederhergestellt.",
    "Kompakter Tab-Modus",
    "Zeigt Tabs nur als Symbole an, auch den aktuell ausgewählten.",
    "Standard-Anzeigemodus",
    "Anzeigemodus beim Öffnen des Suchfensters.",
    "Alle", "Favoriten",
    "Bauen", "Holz", "Stein", "Erze", "Redstone", "Ackerbau", "Nahrung", "Kampf", "Werkzeuge",
    "Verzauberung", "Tränke", "Dekoration", "Nether", "End", "Sonstiges",
    "Anzeigemodus", "Kompaktes Raster", "Großes Raster", "Liste", "Details", "Nur Symbole",
    "Rechtsklick oder Alt+Klick zum Favorisieren",
    "Rechtsklick oder Alt+Klick zum Entfernen aus Favoriten",
    "Noch keine Favoriten. Rechtsklick auf ein Item, um es zu markieren.",
])

# fr_fr
T["fr_fr"] = pack([
    "Activer les onglets de catégorie",
    "Affiche des onglets de style Inventaire Créatif en haut de l'écran de recherche.",
    "Activer les favoris",
    "Marquez les objets d'une ★ pour les trier en haut ou les filtrer dans l'onglet Favoris.",
    "Surbrillance des favoris",
    "Ajoute une lueur dorée aux objets favoris dans la liste.",
    "Mémoriser le dernier mode d'affichage",
    "Si activé, le dernier mode d'affichage est restauré à l'ouverture suivante.",
    "Mode onglet compact",
    "Affiche les onglets en icônes seules, même l'onglet sélectionné.",
    "Mode d'affichage par défaut",
    "Mode d'affichage initial à l'ouverture de l'écran de recherche.",
    "Tout", "Favoris",
    "Construction", "Bois", "Pierre", "Minerais", "Redstone", "Agriculture", "Nourriture", "Combat", "Outils",
    "Enchantement", "Potions", "Décoration", "Nether", "End", "Divers",
    "Mode d'affichage", "Grille compacte", "Grande grille", "Liste", "Détails", "Icônes seules",
    "Clic droit ou Alt+Clic pour ajouter aux favoris",
    "Clic droit ou Alt+Clic pour retirer des favoris",
    "Aucun favori. Clic droit sur un objet pour le marquer.",
])

# es_es
T["es_es"] = pack([
    "Activar pestañas de categoría",
    "Muestra pestañas estilo Inventario Creativo en la parte superior de la búsqueda.",
    "Activar favoritos",
    "Marca objetos con ★ para que aparezcan arriba o filtrarlos en la pestaña Favoritos.",
    "Resaltado de favoritos",
    "Añade un brillo dorado a los objetos favoritos en la lista.",
    "Recordar último modo de visualización",
    "Si está activo, restaura el último modo elegido al abrir de nuevo.",
    "Modo de pestaña compacto",
    "Muestra las pestañas solo como iconos, incluso la seleccionada.",
    "Modo de visualización por defecto",
    "Modo inicial al abrir la pantalla de búsqueda.",
    "Todo", "Favoritos",
    "Construcción", "Madera", "Piedra", "Minerales", "Redstone", "Agricultura", "Comida", "Combate", "Herramientas",
    "Encantar", "Pociones", "Decoración", "Nether", "End", "Otros",
    "Modo de visualización", "Cuadrícula compacta", "Cuadrícula grande", "Lista", "Detallado", "Solo iconos",
    "Clic derecho o Alt+clic para añadir a favoritos",
    "Clic derecho o Alt+clic para quitar de favoritos",
    "Sin favoritos. Haz clic derecho en un objeto para marcarlo.",
])

# it_it
T["it_it"] = pack([
    "Abilita schede categoria",
    "Mostra schede in stile Inventario Creativo nella parte alta della schermata di ricerca.",
    "Abilita preferiti",
    "Aggiungi ★ agli oggetti per ordinarli in cima o filtrarli nella scheda Preferiti.",
    "Evidenzia preferiti",
    "Aggiunge un bagliore dorato agli oggetti preferiti nella lista.",
    "Ricorda ultima modalità",
    "Se attivo, ripristina l'ultima modalità di visualizzazione alla riapertura.",
    "Modalità schede compatta",
    "Mostra le schede solo come icone, anche quella selezionata.",
    "Modalità predefinita",
    "Modalità iniziale all'apertura della schermata di ricerca.",
    "Tutti", "Preferiti",
    "Costruzioni", "Legno", "Pietra", "Minerali", "Redstone", "Agricoltura", "Cibo", "Combattimento", "Strumenti",
    "Incantamenti", "Pozioni", "Decorazione", "Nether", "End", "Altri",
    "Modalità", "Griglia compatta", "Griglia grande", "Elenco", "Dettagliato", "Solo icone",
    "Clic destro o Alt+clic per aggiungere ai preferiti",
    "Clic destro o Alt+clic per rimuovere dai preferiti",
    "Nessun preferito. Clic destro su un oggetto per aggiungere ★.",
])

# pt_br
T["pt_br"] = pack([
    "Ativar abas de categoria",
    "Exibe abas no estilo Inventário Criativo no topo da tela de busca.",
    "Ativar favoritos",
    "Marque itens com ★ para que apareçam no topo ou filtrar na aba Favoritos.",
    "Destaque de favoritos",
    "Adiciona um brilho dourado aos itens favoritados na lista.",
    "Lembrar último modo",
    "Se ativo, restaura o último modo de exibição na próxima abertura.",
    "Modo de aba compacto",
    "Exibe as abas apenas como ícones, mesmo a selecionada.",
    "Modo de exibição padrão",
    "Modo inicial ao abrir a tela de busca.",
    "Tudo", "Favoritos",
    "Construção", "Madeira", "Pedra", "Minérios", "Redstone", "Agricultura", "Comida", "Combate", "Ferramentas",
    "Encantamento", "Poções", "Decoração", "Nether", "End", "Outros",
    "Modo de exibição", "Grade compacta", "Grade grande", "Lista", "Detalhes", "Somente ícones",
    "Clique direito ou Alt+clique para favoritar",
    "Clique direito ou Alt+clique para remover dos favoritos",
    "Sem favoritos. Clique direito em um item para adicionar ★.",
])

# ru_ru
T["ru_ru"] = pack([
    "Вкладки категорий",
    "Показывает вкладки в стиле творческого инвентаря в верхней части поиска.",
    "Включить избранное",
    "Помечайте предметы ★ для сортировки наверх или фильтра во вкладке Избранное.",
    "Подсветка избранного",
    "Добавляет золотое свечение к избранным предметам в списке.",
    "Запоминать режим",
    "Если включено, восстанавливает последний выбранный режим при следующем открытии.",
    "Компактные вкладки",
    "Вкладки только в виде иконок, даже выбранная.",
    "Режим по умолчанию",
    "Начальный режим при открытии экрана поиска.",
    "Все", "Избранное",
    "Строительство", "Дерево", "Камень", "Руды", "Редстоун", "Земледелие", "Еда", "Бой", "Инструменты",
    "Чары", "Зелья", "Декор", "Незер", "Край", "Прочее",
    "Режим отображения", "Компактная сетка", "Крупная сетка", "Список", "Подробно", "Только значки",
    "ПКМ или Alt+клик чтобы добавить в избранное",
    "ПКМ или Alt+клик чтобы убрать из избранного",
    "Нет избранного. Кликните ПКМ по предмету, чтобы добавить ★.",
])

# tr_tr
T["tr_tr"] = pack([
    "Kategori sekmelerini etkinleştir",
    "Arama ekranının üstünde Yaratıcı Envanter tarzı sekmeler gösterir.",
    "Favorileri etkinleştir",
    "Eşyalara ★ vererek üste sırala veya Favoriler sekmesinden filtrele.",
    "Favori vurgusu",
    "Favori eşyalara listede altın bir parlama ekler.",
    "Son görünüm modunu hatırla",
    "Etkinse, sonraki açılışta son seçilen görünüm modu kullanılır.",
    "Kompakt sekme modu",
    "Sekmeleri sadece simge olarak gösterir, seçili sekmeyi de.",
    "Varsayılan görünüm",
    "Arama ekranı açıldığında kullanılacak başlangıç görünümü.",
    "Hepsi", "Favoriler",
    "Yapı", "Ahşap", "Taş", "Cevher", "Redstone", "Çiftçilik", "Yemek", "Savaş", "Aletler",
    "Büyü", "İksir", "Dekorasyon", "Nether", "End", "Diğer",
    "Görünüm modu", "Kompakt ızgara", "Büyük ızgara", "Liste", "Ayrıntılı", "Sadece simge",
    "Favorilere eklemek için sağ tık veya Alt+tık",
    "Favorilerden çıkarmak için sağ tık veya Alt+tık",
    "Favori yok. Bir eşyaya sağ tıklayarak ★ ekleyin.",
])

# ar_sa  (Arabic, RTL)
T["ar_sa"] = pack([
    "تفعيل علامات التصنيف",
    "إظهار علامات تبويب بنمط مخزون الإبداع أعلى شاشة البحث.",
    "تفعيل المفضلة",
    "ضع ★ على العناصر لترتيبها في الأعلى أو لتصفيتها في علامة المفضلة.",
    "إبراز المفضلة",
    "إضافة توهج ذهبي للعناصر المفضّلة في القائمة.",
    "تذكر آخر وضع عرض",
    "عند التفعيل، يُستخدم آخر وضع عرض في المرة القادمة.",
    "وضع العلامات المضغوط",
    "إظهار العلامات كأيقونات فقط حتى المختارة.",
    "وضع العرض الافتراضي",
    "وضع العرض الأولي عند فتح شاشة البحث.",
    "الكل", "المفضلة",
    "بناء", "خشب", "حجر", "خام", "ريدستون", "زراعة", "طعام", "قتال", "أدوات",
    "تعويذة", "جرعات", "زينة", "نذر", "نهاية", "أخرى",
    "وضع العرض", "شبكة مضغوطة", "شبكة كبيرة", "قائمة", "تفصيلي", "أيقونات فقط",
    "نقر يمين أو Alt+نقر للإضافة إلى المفضلة",
    "نقر يمين أو Alt+نقر للإزالة من المفضلة",
    "لا توجد مفضلات. انقر بزر الفأرة الأيمن على عنصر لإضافته ★.",
])

# Languages that fall back to en_us are accepted by the spec (LanguageManager auto-falls back).
# We still add a minimal set for: nl, pl, sv, da, nb, fi, cs, hu, ro, uk, id, ms, vi, th, hi.

T["nl_nl"] = pack([
    "Categorietabs inschakelen",
    "Toon Creative Inventory-achtige tabs bovenaan het zoekscherm.",
    "Favorieten inschakelen",
    "Markeer items met ★ om bovenaan te sorteren of in Favorieten te filteren.",
    "Favorieten markeren",
    "Voeg een gouden gloed toe aan favoriete items.",
    "Laatste weergavemodus onthouden",
    "Indien aan, wordt de laatst gekozen modus de volgende keer hersteld.",
    "Compacte tabmodus",
    "Toon tabs alleen als icoon, ook de geselecteerde.",
    "Standaardweergave",
    "Beginmodus bij het openen van het zoekscherm.",
    "Alles", "Favorieten",
    "Bouwen", "Hout", "Steen", "Erts", "Redstone", "Landbouw", "Voedsel", "Gevecht", "Gereedschap",
    "Betoveren", "Toverdranken", "Decoratie", "Nether", "End", "Overig",
    "Weergavemodus", "Compact raster", "Groot raster", "Lijst", "Gedetailleerd", "Alleen iconen",
    "Rechtermuisklik of Alt+klik om toe te voegen aan favorieten",
    "Rechtermuisklik of Alt+klik om te verwijderen uit favorieten",
    "Nog geen favorieten. Klik met rechts om een item te markeren.",
])

T["pl_pl"] = pack([
    "Włącz zakładki kategorii",
    "Pokazuje zakładki w stylu kreatywnego ekwipunku na górze ekranu wyszukiwania.",
    "Włącz ulubione",
    "Oznacz przedmioty ★, aby były na górze lub filtrowane w zakładce Ulubione.",
    "Podświetlanie ulubionych",
    "Dodaje złoty blask do ulubionych przedmiotów.",
    "Pamiętaj ostatni tryb",
    "Gdy włączone, przywraca ostatnio wybrany tryb przy ponownym otwarciu.",
    "Kompaktowe zakładki",
    "Pokazuje zakładki tylko jako ikony, nawet wybraną.",
    "Domyślny tryb",
    "Tryb startowy po otwarciu ekranu wyszukiwania.",
    "Wszystko", "Ulubione",
    "Budowa", "Drewno", "Kamień", "Rudy", "Redstone", "Rolnictwo", "Jedzenie", "Walka", "Narzędzia",
    "Zaklęcia", "Mikstury", "Dekoracja", "Nether", "End", "Inne",
    "Tryb wyświetlania", "Zwarta siatka", "Duża siatka", "Lista", "Szczegóły", "Tylko ikony",
    "PPM lub Alt+klik, aby dodać do ulubionych",
    "PPM lub Alt+klik, aby usunąć z ulubionych",
    "Brak ulubionych. Kliknij PPM na przedmiot, aby oznaczyć ★.",
])

T["sv_se"] = pack([
    "Aktivera kategoriflikar",
    "Visa flikar i Creative-stil överst i sökskärmen.",
    "Aktivera favoriter",
    "Markera föremål med ★ för att sortera överst eller filtrera under Favoriter.",
    "Markera favoriter",
    "Lägg till ett gyllene sken till favoritföremål.",
    "Kom ihåg senaste läge",
    "Återställer senaste visningsläge nästa gång.",
    "Kompakt flikläge",
    "Visa flikar endast som ikoner, även den valda.",
    "Standardläge",
    "Startläge när sökskärmen öppnas.",
    "Alla", "Favoriter",
    "Byggnad", "Trä", "Sten", "Malm", "Redstone", "Jordbruk", "Mat", "Strid", "Verktyg",
    "Förtrollning", "Trolldrycker", "Dekoration", "Nether", "End", "Övrigt",
    "Visningsläge", "Kompakt rutnät", "Stort rutnät", "Lista", "Detaljerat", "Endast ikoner",
    "Högerklicka eller Alt+klick för att favoritmarkera",
    "Högerklicka eller Alt+klick för att ta bort favorit",
    "Inga favoriter. Högerklicka för att markera ★.",
])

T["da_dk"] = pack([
    "Aktivér kategorifaner",
    "Vis faner i Creative-stil øverst på søgeskærmen.",
    "Aktivér favoritter",
    "Marker emner med ★ for at sortere øverst eller filtrere under Favoritter.",
    "Marker favoritter",
    "Tilføj en gylden glød til favoritemner.",
    "Husk seneste tilstand",
    "Hvis aktivt, genskabes seneste visning næste gang.",
    "Kompakt fanetilstand",
    "Vis faner kun som ikoner, også den valgte.",
    "Standardvisning",
    "Starttilstand når søgeskærmen åbnes.",
    "Alle", "Favoritter",
    "Byggeri", "Træ", "Sten", "Malm", "Redstone", "Landbrug", "Mad", "Kamp", "Værktøj",
    "Forhekselse", "Eliksirer", "Dekoration", "Nether", "End", "Øvrigt",
    "Visningstilstand", "Kompakt gitter", "Stort gitter", "Liste", "Detaljeret", "Kun ikoner",
    "Højreklik eller Alt+klik for at favoritmarkere",
    "Højreklik eller Alt+klik for at fjerne favorit",
    "Ingen favoritter. Højreklik på et emne for at markere ★.",
])

T["nb_no"] = pack([
    "Aktiver kategorifaner",
    "Vis faner i kreativ-stil øverst på søkeskjermen.",
    "Aktiver favoritter",
    "Marker gjenstander med ★ for å sortere øverst eller filtrere i Favoritter.",
    "Fremhev favoritter",
    "Legg til et gyllent skinn på favorittgjenstander.",
    "Husk siste modus",
    "Hvis på, gjenopprettes siste visningsmodus neste gang.",
    "Kompakt fanemodus",
    "Vis faner kun som ikoner, også valgt fane.",
    "Standardvisning",
    "Startmodus når søkeskjermen åpnes.",
    "Alle", "Favoritter",
    "Bygg", "Tre", "Stein", "Malm", "Redstone", "Jordbruk", "Mat", "Kamp", "Verktøy",
    "Fortryllelse", "Drikker", "Dekorasjon", "Nether", "End", "Annet",
    "Visningsmodus", "Kompakt rutenett", "Stort rutenett", "Liste", "Detaljert", "Kun ikoner",
    "Høyreklikk eller Alt+klikk for å favorittmarkere",
    "Høyreklikk eller Alt+klikk for å fjerne favoritt",
    "Ingen favoritter. Høyreklikk på en gjenstand for ★.",
])

T["fi_fi"] = pack([
    "Ota kategorialehdet käyttöön",
    "Näytä luovan inventaarion tyyliset välilehdet hakunäytön yläosassa.",
    "Ota suosikit käyttöön",
    "Merkitse esineet ★:lla nostaaksesi ne ylös tai suodattaaksesi Suosikit-välilehdellä.",
    "Korosta suosikit",
    "Lisää kultainen hehku suosikkiesineisiin.",
    "Muista viimeinen näkymä",
    "Käytössä palauttaa edellisen näkymätilan ensi avauksella.",
    "Tiivis välilehtitila",
    "Näytä välilehdet vain kuvakkeina, myös valittu.",
    "Oletusnäkymä",
    "Aloitusnäkymä hakunäyttöä avattaessa.",
    "Kaikki", "Suosikit",
    "Rakentaminen", "Puu", "Kivi", "Malmi", "Redstone", "Maatalous", "Ruoka", "Taistelu", "Työkalut",
    "Lumous", "Juomat", "Sisustus", "Nether", "End", "Muut",
    "Näkymä", "Tiivis ruudukko", "Suuri ruudukko", "Lista", "Yksityiskohtainen", "Vain kuvakkeet",
    "Hiiren oikealla tai Alt+klik lisätäksesi suosikkeihin",
    "Hiiren oikealla tai Alt+klik poistaaksesi suosikeista",
    "Ei suosikkeja. Klikkaa hiiren oikealla lisätäksesi ★.",
])

T["cs_cz"] = pack([
    "Povolit kategorické záložky",
    "Zobrazí záložky ve stylu Kreativního inventáře v horní části obrazovky hledání.",
    "Povolit oblíbené",
    "Označte předměty ★ pro řazení nahoru nebo filtrování v záložce Oblíbené.",
    "Zvýraznění oblíbených",
    "Přidá zlatou záři k oblíbeným předmětům.",
    "Zapamatovat poslední režim",
    "Pokud zapnuto, obnoví poslední režim zobrazení při dalším otevření.",
    "Kompaktní záložky",
    "Zobrazit záložky pouze jako ikony, i vybranou.",
    "Výchozí režim",
    "Počáteční režim při otevření obrazovky hledání.",
    "Vše", "Oblíbené",
    "Stavba", "Dřevo", "Kámen", "Rudy", "Redstone", "Zemědělství", "Jídlo", "Boj", "Nástroje",
    "Očarování", "Lektvary", "Dekorace", "Nether", "End", "Ostatní",
    "Režim zobrazení", "Kompaktní mřížka", "Velká mřížka", "Seznam", "Podrobné", "Pouze ikony",
    "Pravé tlačítko nebo Alt+klik pro přidání do oblíbených",
    "Pravé tlačítko nebo Alt+klik pro odebrání z oblíbených",
    "Žádné oblíbené. Klikněte pravým tlačítkem pro označení ★.",
])

T["hu_hu"] = pack([
    "Kategória fülek engedélyezése",
    "Creative inventory-stílusú füleket jelenít meg a kereső tetején.",
    "Kedvencek engedélyezése",
    "Jelöld meg az elemeket ★-gal a felülre rendezéshez vagy szűréshez a Kedvencek fülön.",
    "Kedvencek kiemelése",
    "Aranysárga ragyogást ad a kedvencként megjelölt elemeknek.",
    "Utolsó nézet megjegyzése",
    "Bekapcsolva visszaállítja az utoljára választott nézetet legközelebb.",
    "Kompakt fül mód",
    "A fülek csak ikonként jelennek meg, még a kiválasztott is.",
    "Alapértelmezett nézet",
    "Kezdeti nézet a kereső megnyitásakor.",
    "Mind", "Kedvencek",
    "Építkezés", "Fa", "Kő", "Érc", "Redstone", "Mezőgazdaság", "Étel", "Harc", "Eszközök",
    "Varázslás", "Bájitalok", "Dekoráció", "Nether", "End", "Egyéb",
    "Megjelenítés", "Kompakt rács", "Nagy rács", "Lista", "Részletes", "Csak ikon",
    "Jobb klikk vagy Alt+klikk kedvencekhez adáshoz",
    "Jobb klikk vagy Alt+klikk kedvencekből eltávolításhoz",
    "Nincs kedvenc. Kattints jobb klikkel egy elemre a ★ jelölésért.",
])

T["ro_ro"] = pack([
    "Activează file de categorie",
    "Afișează file în stil Inventar Creativ în partea de sus a căutării.",
    "Activează favoritele",
    "Marchează obiectele cu ★ pentru a le sorta sus sau filtra în fila Favorite.",
    "Evidențiere favorite",
    "Adaugă o strălucire aurie obiectelor favorite din listă.",
    "Reține ultimul mod",
    "Activ restabilește ultimul mod ales la următoarea deschidere.",
    "Mod compact pentru file",
    "Afișează filele doar ca pictograme, inclusiv cea selectată.",
    "Mod implicit",
    "Modul inițial la deschiderea ecranului de căutare.",
    "Toate", "Favorite",
    "Construcții", "Lemn", "Piatră", "Minereuri", "Redstone", "Agricultură", "Mâncare", "Luptă", "Unelte",
    "Vrăjire", "Poțiuni", "Decor", "Nether", "End", "Diverse",
    "Mod afișare", "Grilă compactă", "Grilă mare", "Listă", "Detaliat", "Doar pictograme",
    "Click dreapta sau Alt+click pentru a adăuga la favorite",
    "Click dreapta sau Alt+click pentru a elimina din favorite",
    "Niciun favorit. Click dreapta pe un obiect pentru a-l marca ★.",
])

T["uk_ua"] = pack([
    "Увімкнути вкладки категорій",
    "Показує вкладки у стилі творчого інвентаря вгорі екрана пошуку.",
    "Увімкнути обране",
    "Позначайте предмети ★ для сортування зверху або фільтра у вкладці Обране.",
    "Підсвічування обраного",
    "Додає золоте сяйво обраним предметам у списку.",
    "Запам'ятовувати режим",
    "Якщо ввімкнено, відновлює останній режим відображення наступного разу.",
    "Компактні вкладки",
    "Показує вкладки лише як іконки, навіть обрану.",
    "Режим за замовчуванням",
    "Початковий режим при відкритті екрана пошуку.",
    "Усе", "Обране",
    "Будівництво", "Дерево", "Камінь", "Руди", "Редстоун", "Землеробство", "Їжа", "Бій", "Інструменти",
    "Чари", "Зілля", "Декор", "Незер", "Енд", "Інше",
    "Режим відображення", "Компактна сітка", "Велика сітка", "Список", "Детально", "Лише іконки",
    "ПКМ або Alt+клік, щоб додати в обране",
    "ПКМ або Alt+клік, щоб прибрати з обраного",
    "Немає обраного. Клацніть ПКМ по предмету, щоб додати ★.",
])

T["id_id"] = pack([
    "Aktifkan tab kategori",
    "Menampilkan tab gaya Inventaris Kreatif di atas layar pencarian.",
    "Aktifkan favorit",
    "Beri ★ pada item untuk diurutkan ke atas atau difilter di tab Favorit.",
    "Sorot favorit",
    "Menambahkan cahaya emas pada item favorit.",
    "Ingat mode terakhir",
    "Jika aktif, mode tampilan terakhir akan dipulihkan saat dibuka kembali.",
    "Mode tab ringkas",
    "Tampilkan tab hanya sebagai ikon, termasuk yang dipilih.",
    "Mode tampilan default",
    "Mode awal saat layar pencarian dibuka.",
    "Semua", "Favorit",
    "Bangunan", "Kayu", "Batu", "Bijih", "Redstone", "Pertanian", "Makanan", "Tempur", "Alat",
    "Enchant", "Ramuan", "Dekorasi", "Nether", "End", "Lainnya",
    "Mode tampilan", "Grid ringkas", "Grid besar", "Daftar", "Rinci", "Hanya ikon",
    "Klik kanan atau Alt+klik untuk menambah favorit",
    "Klik kanan atau Alt+klik untuk menghapus dari favorit",
    "Belum ada favorit. Klik kanan untuk menandai ★.",
])

T["ms_my"] = pack([
    "Aktifkan tab kategori",
    "Menunjukkan tab gaya Inventori Kreatif di bahagian atas skrin carian.",
    "Aktifkan kegemaran",
    "Tanda ★ pada item untuk disusun di atas atau ditapis di tab Kegemaran.",
    "Tonjolkan kegemaran",
    "Menambah pijar keemasan pada item kegemaran.",
    "Ingat mod terakhir",
    "Jika aktif, mod paparan terakhir dipulihkan pada kali seterusnya.",
    "Mod tab ringkas",
    "Paparkan tab hanya sebagai ikon, termasuk yang dipilih.",
    "Mod paparan lalai",
    "Mod awal apabila skrin carian dibuka.",
    "Semua", "Kegemaran",
    "Bangunan", "Kayu", "Batu", "Bijih", "Redstone", "Pertanian", "Makanan", "Pertempuran", "Alat",
    "Sihir", "Ramuan", "Hiasan", "Nether", "End", "Lain-lain",
    "Mod paparan", "Grid ringkas", "Grid besar", "Senarai", "Terperinci", "Ikon sahaja",
    "Klik kanan atau Alt+klik untuk tambah kegemaran",
    "Klik kanan atau Alt+klik untuk buang kegemaran",
    "Tiada kegemaran. Klik kanan pada item untuk tandakan ★.",
])

T["vi_vn"] = pack([
    "Bật thẻ phân loại",
    "Hiển thị thẻ phong cách Sáng tạo phía trên màn hình tìm kiếm.",
    "Bật yêu thích",
    "Đánh dấu ★ để sắp xếp lên đầu hoặc lọc trong thẻ Yêu thích.",
    "Làm nổi bật yêu thích",
    "Thêm ánh vàng cho các vật yêu thích.",
    "Nhớ chế độ trước",
    "Nếu bật, khôi phục chế độ hiển thị lần trước khi mở lại.",
    "Chế độ thẻ thu gọn",
    "Hiển thị thẻ chỉ là biểu tượng, kể cả thẻ đã chọn.",
    "Chế độ mặc định",
    "Chế độ ban đầu khi mở màn hình tìm kiếm.",
    "Tất cả", "Yêu thích",
    "Xây dựng", "Gỗ", "Đá", "Quặng", "Redstone", "Nông nghiệp", "Thực phẩm", "Chiến đấu", "Công cụ",
    "Phù phép", "Thuốc", "Trang trí", "Nether", "End", "Khác",
    "Chế độ hiển thị", "Lưới gọn", "Lưới lớn", "Danh sách", "Chi tiết", "Chỉ biểu tượng",
    "Chuột phải hoặc Alt+click để thêm yêu thích",
    "Chuột phải hoặc Alt+click để bỏ yêu thích",
    "Chưa có yêu thích. Chuột phải vào vật để gắn ★.",
])

T["th_th"] = pack([
    "เปิดแท็บหมวดหมู่",
    "แสดงแท็บสไตล์ Creative Inventory ที่ด้านบนของหน้าค้นหา",
    "เปิดรายการโปรด",
    "ติด ★ ให้ไอเทมเพื่อจัดเรียงขึ้นบนหรือกรองในแท็บโปรด",
    "ไฮไลต์รายการโปรด",
    "เพิ่มแสงเรืองทองให้ไอเทมโปรดในรายการ",
    "จดจำโหมดล่าสุด",
    "เมื่อเปิด ระบบจะกลับมาที่โหมดล่าสุดเมื่อเปิดอีกครั้ง",
    "โหมดแท็บกะทัดรัด",
    "แสดงแท็บเป็นไอคอนเท่านั้น แม้แท็บที่เลือก",
    "โหมดแสดงผลเริ่มต้น",
    "โหมดเริ่มต้นเมื่อเปิดหน้าค้นหา",
    "ทั้งหมด", "รายการโปรด",
    "ก่อสร้าง", "ไม้", "หิน", "แร่", "เรดสโตน", "เกษตร", "อาหาร", "ต่อสู้", "เครื่องมือ",
    "เวทมนตร์", "ยา", "ตกแต่ง", "เนเธอร์", "เอนด์", "อื่น ๆ",
    "โหมดแสดงผล", "ตารางกะทัดรัด", "ตารางใหญ่", "รายการ", "ละเอียด", "เฉพาะไอคอน",
    "คลิกขวาหรือ Alt+คลิก เพื่อเพิ่มในรายการโปรด",
    "คลิกขวาหรือ Alt+คลิก เพื่อลบออกจากรายการโปรด",
    "ยังไม่มีรายการโปรด คลิกขวาที่ไอเทมเพื่อใส่ ★",
])

T["hi_in"] = pack([
    "श्रेणी टैब सक्षम करें",
    "खोज स्क्रीन के शीर्ष पर क्रिएटिव इन्वेंट्री शैली के टैब दिखाएं।",
    "पसंदीदा सक्षम करें",
    "★ लगाकर शीर्ष पर सॉर्ट करें या पसंदीदा टैब में फ़िल्टर करें।",
    "पसंदीदा हाइलाइट",
    "पसंदीदा वस्तुओं को सुनहरी चमक देता है।",
    "अंतिम मोड याद रखें",
    "चालू होने पर अगली बार पिछला मोड पुनर्स्थापित होता है।",
    "कॉम्पैक्ट टैब मोड",
    "टैब केवल आइकन के रूप में दिखाएं, चयनित भी।",
    "डिफ़ॉल्ट मोड",
    "खोज स्क्रीन खोलते समय प्रारंभिक मोड।",
    "सभी", "पसंदीदा",
    "निर्माण", "लकड़ी", "पत्थर", "अयस्क", "रेडस्टोन", "खेती", "भोजन", "लड़ाई", "उपकरण",
    "मंत्र", "औषधि", "सजावट", "नेदर", "एंड", "अन्य",
    "दृश्य मोड", "कॉम्पैक्ट ग्रिड", "बड़ा ग्रिड", "सूची", "विस्तृत", "केवल आइकन",
    "पसंदीदा में जोड़ने के लिए राइट क्लिक या Alt+क्लिक",
    "पसंदीदा से हटाने के लिए राइट क्लिक या Alt+क्लिक",
    "कोई पसंदीदा नहीं। आइटम पर राइट क्लिक करके ★ लगाएं।",
])


def merge(file: Path, additions: dict) -> int:
    with file.open("r", encoding="utf-8") as f:
        data = json.load(f)
    added = 0
    for k, v in additions.items():
        if k not in data:
            data[k] = v
            added += 1
    if added == 0:
        return 0
    with file.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return added


def main():
    total = 0
    for lang_file in sorted(LANG_DIR.glob("*.json")):
        code = lang_file.stem
        if code == "en_us":
            continue  # already updated
        if code in T:
            added = merge(lang_file, T[code])
            print(f"{code}: +{added}")
            total += added
        else:
            # No native translation: leave the file alone — LanguageManager falls back to en_us automatically.
            print(f"{code}: (uses en_us fallback)")
    print(f"\nTotal keys added: {total}")


if __name__ == "__main__":
    main()
