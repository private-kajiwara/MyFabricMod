#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OmniChest: 新規追加機能 (Reset 確認 Popup / Beacon 演出 / Mod Menu 説明) の翻訳キーを
全 lang ファイルへ一括追加するワンショット スクリプト。

- 既存の整形・キー順は壊さず、 末尾の `}` の直前へ新規キー群を挿入する。
- 既に追加済み (modmenu.descriptionTranslation.omnichest が存在) のファイルはスキップ (= 冪等)。
- 追加後に json.loads で各ファイルの妥当性を検証する。
"""

import json
import pathlib
import sys

LANG_DIR = pathlib.Path("fabric/src/client/resources/assets/omnichest/lang")

# 追加するキーの順序 (= 挿入時の並び)。
KEY_ORDER = [
    "modmenu.descriptionTranslation.omnichest",
    "omnichest.reset_popup.title",
    "omnichest.reset_popup.yes",
    "omnichest.reset_popup.no",
    "omnichest.reset_popup.no_changes",
    "config.omnichest.sub.search.beacon",
    "config.omnichest.search.enableBeacon",
    "config.omnichest.search.enableBeacon.tooltip",
    "config.omnichest.search.beaconOpacity",
    "config.omnichest.search.beaconOpacity.tooltip",
    "config.omnichest.search.beaconWidth",
    "config.omnichest.search.beaconWidth.tooltip",
    "config.omnichest.search.beaconDistanceFade",
    "config.omnichest.search.beaconDistanceFade.tooltip",
    "config.omnichest.search.beaconAnimation",
    "config.omnichest.search.beaconAnimation.tooltip",
]

# lang -> (desc, title, yes, no, no_changes, beaconSub,
#          enB, enB.t, op, op.t, w, w.t, df, df.t, anim, anim.t)
T = {
"en_us": ["Find any item across all your chests at a glance. OmniChest adds a network-wide search with in-world pins, smart deposit, category sorting, slot locks, and reusable chest templates — one toolkit to keep your storage tidy.",
  "Really reset all changes?", "Yes", "No", "All settings are at default values.",
  "Beacon Effect",
  "Enable Beacon Effect", "Draw a translucent beacon-like beam rising from each search pin so it stays visible from far away. Pins themselves are unchanged.",
  "Beacon Opacity", "Base opacity of the beam. Combined with fade and pulse.",
  "Beacon Width", "Width of the beam core in blocks. The outer glow is wider.",
  "Distance Fade", "Dim the beam gradually with distance (it never fully disappears).",
  "Beacon Animation", "Enable a slow pulsing brightness animation on the beam."],

"ja_jp": ["すべてのチェストから目的のアイテムを一目で発見。倉庫横断検索とワールド上のピン表示に加え、スマート預入・カテゴリ整理・スロットロック・再利用できるチェストテンプレートを備えた、収納整理の総合ツールです。",
  "本当に変更内容をリセットしますか？", "はい", "いいえ", "すべて既定値のままです。",
  "ビーコン演出",
  "ビーコン演出を有効化", "検索ピンから上空へ伸びる半透明のビーコン風ビームを描画し、遠くからでも見つけやすくします。ピン自体は変更されません。",
  "ビーム不透明度", "ビームの基準不透明度。フェードや明滅と掛け合わさります。",
  "ビーム幅", "ビーム中心柱の幅 (ブロック)。外周グローはこれより太くなります。",
  "距離フェード", "距離に応じてビームを徐々に薄くします (完全には消えません)。",
  "ビーコンアニメーション", "ビームにゆっくりした明滅アニメーションを付けます。"],

"ko_kr": ["모든 상자 속 아이템을 한눈에 찾으세요. OmniChest는 창고 통합 검색과 월드 내 핀 표시는 물론, 스마트 보관·카테고리 정리·슬롯 잠금·재사용 가능한 상자 템플릿까지 갖춘 정리 도구 모음입니다.",
  "정말 모든 변경 사항을 초기화할까요?", "예", "아니오", "모든 설정이 기본값입니다.",
  "신호기 효과",
  "신호기 효과 사용", "검색 핀에서 하늘로 뻗는 반투명 신호기 빔을 그려 멀리서도 잘 보이게 합니다. 핀 자체는 변경되지 않습니다.",
  "빔 불투명도", "빔의 기본 불투명도. 페이드 및 깜빡과 결합됩니다.",
  "빔 너비", "빔 중심 기둥의 너비(블록). 외곽 광선은 더 넓습니다.",
  "거리 페이드", "거리에 따라 빔을 점차 흐릿하게 합니다(완전히 사라지지는 않음).",
  "신호기 애니메이션", "빔에 느리게 깜빡이는 밝기 애니메이션을 켭니다."],

"zh_cn": ["一眼找到所有箱子里的物品。OmniChest 提供跨仓库搜索与世界内标记，还有智能存放、分类整理、槽位锁定和可复用的箱子模板，是让储物井然有序的一体化工具。",
  "确定要重置所有更改吗？", "是", "否", "所有设置均为默认值。",
  "信标效果",
  "启用信标效果", "从每个搜索标记向上射出半透明的信标光束，让你在远处也能看到。标记本身不变。",
  "信标不透明度", "光束的基础不透明度，会与淡化和脉动叠加。",
  "信标宽度", "光束核心的宽度（方块）。外层光晕更宽。",
  "距离淡化", "随距离逐渐变淡（不会完全消失）。",
  "信标动画", "为光束启用缓慢的脉动亮度动画。"],

"zh_tw": ["一眼找到所有箱子裡的物品。OmniChest 提供跨倉庫搜尋與世界內標記，還有智慧存放、分類整理、欄位鎖定和可重複使用的箱子範本，是讓收納井然有序的整合工具。",
  "確定要重設所有變更嗎？", "是", "否", "所有設定皆為預設值。",
  "信標效果",
  "啟用信標效果", "從每個搜尋標記向上射出半透明的信標光束，讓你在遠處也能看見。標記本身不變。",
  "信標不透明度", "光束的基礎不透明度，會與淡化和脈動疊加。",
  "信標寬度", "光束核心的寬度（方塊）。外層光暈更寬。",
  "距離淡化", "隨距離逐漸變淡（不會完全消失）。",
  "信標動畫", "為光束啟用緩慢的脈動亮度動畫。"],

"es_es": ["Encuentra cualquier objeto en todos tus cofres de un vistazo. OmniChest añade búsqueda en red con marcadores en el mundo, depósito inteligente, ordenado por categorías, bloqueo de ranuras y plantillas de cofres reutilizables: una única herramienta para mantener ordenado tu almacenamiento.",
  "¿Restablecer todos los cambios?", "Sí", "No", "Todos los ajustes están en sus valores predeterminados.",
  "Efecto de baliza",
  "Activar efecto de baliza", "Dibuja un haz translúcido tipo baliza que sube desde cada marcador de búsqueda para verlo desde lejos. Los marcadores no cambian.",
  "Opacidad de la baliza", "Opacidad base del haz. Se combina con el desvanecimiento y el pulso.",
  "Anchura de la baliza", "Anchura del núcleo del haz en bloques. El resplandor exterior es más ancho.",
  "Desvanecimiento por distancia", "Atenúa el haz gradualmente con la distancia (nunca desaparece del todo).",
  "Animación de la baliza", "Activa una animación de brillo pulsante lento en el haz."],

"de_de": ["Finde jeden Gegenstand in all deinen Kisten auf einen Blick. OmniChest bietet eine netzwerkweite Suche mit Markierungen in der Welt, dazu smartes Einlagern, Kategoriesortierung, Slot-Sperren und wiederverwendbare Kistenvorlagen – ein Werkzeugkasten für ordentliche Lager.",
  "Wirklich alle Änderungen zurücksetzen?", "Ja", "Nein", "Alle Einstellungen sind auf Standardwerten.",
  "Leuchtfeuer-Effekt",
  "Leuchtfeuer-Effekt aktivieren", "Zeichnet einen durchscheinenden Leuchtfeuer-Strahl, der von jeder Such-Markierung aufsteigt, damit sie aus der Ferne sichtbar bleibt. Die Markierungen selbst bleiben unverändert.",
  "Strahl-Deckkraft", "Grunddeckkraft des Strahls. Wird mit Ausblenden und Pulsieren kombiniert.",
  "Strahlbreite", "Breite des Strahlkerns in Blöcken. Das äußere Leuchten ist breiter.",
  "Entfernungs-Ausblendung", "Blendet den Strahl mit der Entfernung allmählich ab (verschwindet nie ganz).",
  "Leuchtfeuer-Animation", "Aktiviert eine langsam pulsierende Helligkeitsanimation am Strahl."],

"it_it": ["Trova qualsiasi oggetto in tutte le tue casse a colpo d'occhio. OmniChest aggiunge una ricerca in rete con segnalini nel mondo, deposito intelligente, ordinamento per categorie, blocco degli slot e modelli di cassa riutilizzabili: un unico strumento per tenere in ordine il magazzino.",
  "Ripristinare davvero tutte le modifiche?", "Sì", "No", "Tutte le impostazioni sono ai valori predefiniti.",
  "Effetto faro",
  "Attiva effetto faro", "Disegna un fascio traslucido in stile faro che si alza da ogni segnalino di ricerca, così resta visibile da lontano. I segnalini non cambiano.",
  "Opacità del faro", "Opacità di base del fascio. Si combina con dissolvenza e pulsazione.",
  "Larghezza del faro", "Larghezza del nucleo del fascio in blocchi. Il bagliore esterno è più ampio.",
  "Dissolvenza con la distanza", "Attenua gradualmente il fascio con la distanza (non scompare mai del tutto).",
  "Animazione del faro", "Attiva un'animazione di luminosità pulsante lenta sul fascio."],

"fr_fr": ["Trouvez n'importe quel objet dans tous vos coffres d'un coup d'œil. OmniChest ajoute une recherche en réseau avec des repères dans le monde, un dépôt intelligent, un tri par catégories, le verrouillage d'emplacements et des modèles de coffre réutilisables : une boîte à outils pour garder un stockage bien rangé.",
  "Vraiment réinitialiser toutes les modifications ?", "Oui", "Non", "Tous les paramètres sont aux valeurs par défaut.",
  "Effet de balise",
  "Activer l'effet de balise", "Dessine un faisceau translucide façon balise qui s'élève de chaque repère de recherche pour rester visible de loin. Les repères eux-mêmes ne changent pas.",
  "Opacité de la balise", "Opacité de base du faisceau. Combinée au fondu et à la pulsation.",
  "Largeur de la balise", "Largeur du cœur du faisceau en blocs. La lueur extérieure est plus large.",
  "Fondu selon la distance", "Atténue progressivement le faisceau avec la distance (sans jamais disparaître totalement).",
  "Animation de la balise", "Active une animation de luminosité pulsée lente sur le faisceau."],

"ru_ru": ["Находите любой предмет во всех сундуках с первого взгляда. OmniChest добавляет поиск по всей сети сундуков с метками в мире, умное складирование, сортировку по категориям, блокировку слотов и многоразовые шаблоны сундуков — единый инструмент для порядка в хранилище.",
  "Действительно сбросить все изменения?", "Да", "Нет", "Все настройки на значениях по умолчанию.",
  "Эффект маяка",
  "Включить эффект маяка", "Рисует полупрозрачный луч в стиле маяка, поднимающийся от каждой метки поиска, чтобы её было видно издалека. Сами метки не меняются.",
  "Непрозрачность маяка", "Базовая непрозрачность луча. Сочетается с затуханием и пульсацией.",
  "Ширина маяка", "Ширина ядра луча в блоках. Внешнее свечение шире.",
  "Затухание по расстоянию", "Постепенно ослабляет луч с расстоянием (никогда не исчезает полностью).",
  "Анимация маяка", "Включает медленную пульсацию яркости луча."],

"pt_br": ["Encontre qualquer item em todos os seus baús num relance. O OmniChest adiciona busca em rede com marcadores no mundo, depósito inteligente, organização por categorias, travas de espaço e modelos de baú reutilizáveis — uma ferramenta completa para manter o armazenamento em ordem.",
  "Redefinir mesmo todas as alterações?", "Sim", "Não", "Todas as configurações estão nos valores padrão.",
  "Efeito de sinalizador",
  "Ativar efeito de sinalizador", "Desenha um feixe translúcido estilo sinalizador subindo de cada marcador de busca para vê-lo de longe. Os marcadores em si não mudam.",
  "Opacidade do sinalizador", "Opacidade base do feixe. Combinada com o esmaecimento e a pulsação.",
  "Largura do sinalizador", "Largura do núcleo do feixe em blocos. O brilho externo é mais largo.",
  "Esmaecimento por distância", "Reduz o feixe gradualmente com a distância (nunca some por completo).",
  "Animação do sinalizador", "Ativa uma animação de brilho pulsante lento no feixe."],

"tr_tr": ["Tüm sandıklarındaki her eşyayı bir bakışta bul. OmniChest; dünya üzerinde işaretlerle ağ genelinde arama, akıllı yerleştirme, kategoriye göre sıralama, yuva kilitleme ve yeniden kullanılabilir sandık şablonları ekler — deponu düzenli tutmak için tek araç.",
  "Tüm değişiklikler gerçekten sıfırlansın mı?", "Evet", "Hayır", "Tüm ayarlar varsayılan değerlerinde.",
  "Fener efekti",
  "Fener efektini etkinleştir", "Her arama işaretinden yükselen yarı saydam fener ışını çizer, böylece uzaktan görünür kalır. İşaretlerin kendisi değişmez.",
  "Fener saydamlığı", "Işının temel opaklığı. Solma ve nabız ile birleşir.",
  "Fener genişliği", "Işın çekirdeğinin blok cinsinden genişliği. Dış parıltı daha geniştir.",
  "Mesafe solması", "Işını mesafeyle kademeli olarak soldurur (asla tamamen kaybolmaz).",
  "Fener animasyonu", "Işında yavaş, nabız gibi parlaklık animasyonu açar."],

"ar_sa": ["اعثر على أي عنصر في كل صناديقك بنظرة واحدة. يضيف OmniChest بحثًا عبر شبكة الصناديق مع علامات في العالم، إضافةً إلى الإيداع الذكي والفرز حسب الفئات وقفل الخانات وقوالب صناديق قابلة لإعادة الاستخدام — أداة واحدة للحفاظ على تخزين منظّم.",
  "هل تريد فعلاً إعادة ضبط كل التغييرات؟", "نعم", "لا", "جميع الإعدادات على القيم الافتراضية.",
  "تأثير المنارة",
  "تفعيل تأثير المنارة", "يرسم شعاعًا شفافًا يشبه المنارة يرتفع من كل علامة بحث ليبقى مرئيًا من بعيد. العلامات نفسها لا تتغير.",
  "عتامة المنارة", "العتامة الأساسية للشعاع. تُدمج مع التلاشي والنبض.",
  "عرض المنارة", "عرض قلب الشعاع بالكتل. التوهج الخارجي أوسع.",
  "تلاشٍ حسب المسافة", "يُخفت الشعاع تدريجيًا مع المسافة (لا يختفي تمامًا أبدًا).",
  "حركة المنارة", "يُفعّل حركة سطوع نابضة بطيئة على الشعاع."],

"hi_in": ["अपने सभी बक्सों में कोई भी वस्तु एक नज़र में खोजें। OmniChest दुनिया में चिह्नों के साथ नेटवर्क-व्यापी खोज जोड़ता है, साथ ही स्मार्ट जमा, श्रेणी छँटाई, स्लॉट लॉक और पुन: उपयोग योग्य बक्सा टेम्पलेट — भंडारण को व्यवस्थित रखने का एक ही उपकरण।",
  "क्या वाकई सभी बदलाव रीसेट करें?", "हाँ", "नहीं", "सभी सेटिंग्स ड़िफ़ॉल्ट मान पर हैं।",
  "बीकन प्रभाव",
  "बीकन प्रभाव सक्षम करें", "हर खोज चिह्न से ऊपर उठती एक पारभासी बीकन किरण बनाता है ताकि वह दूर से भी दिखे। चिह्न स्वयं नहीं बदलते।",
  "बीकन अपारदर्शिता", "किरण की आधार अपारदर्शिता। फ़ेड और स्पंदन के साथ मिलती है।",
  "बीकन चौड़ाई", "किरण के कोर की चौड़ाई (ब्लॉक)। बाहरी चमक अधिक चौड़ी होती है।",
  "दूरी फ़ेड", "दूरी के साथ किरण को धीरे-धीरे मद्धम करता है (कभी पूरी तरह नहीं मिटती)।",
  "बीकन एनिमेशन", "किरण पर धीमी स्पंदित चमक एनिमेशन चालू करता है।"],

"th_th": ["ค้นหาไอเท็มในหีบทุกใบได้ในพริบตา OmniChest เพิ่มการค้นหาทั่วเครือข่ายพร้อมหมุดในโลก รวมถึงการฝากอัจฉริยะ การจัดเรียงตามหมวดหมู่ การล็อกช่อง และเทมเพลตหีบที่ใช้ซ้ำได้ — เครื่องมือเดียวที่ทำให้ที่เก็บของเป็นระเบียบ",
  "ต้องการรีเซ็ตการเปลี่ยนแปลงทั้งหมดจริงหรือ?", "ใช่", "ไม่", "การตั้งค่าทั้งหมดเป็นค่าเริ่มต้น",
  "เอฟเฟกต์บีคอน",
  "เปิดเอฟเฟกต์บีคอน", "วาดลำแสงโปร่งแสงคล้ายบีคอนพุ่งขึ้นจากหมุดค้นหาแต่ละจุด เพื่อให้มองเห็นได้แต่ไกล ตัวหมุดไม่เปลี่ยนแปลง",
  "ความทึบของบีคอน", "ความทึบพื้นฐานของลำแสง รวมกับการจางและการกะพริบ",
  "ความกว้างของบีคอน", "ความกว้างของแกนลำแสง (บล็อก) แสงเรืองรอบนอกจะกว้างกว่า",
  "การจางตามระยะ", "หรี่ลำแสงลงทีละน้อยตามระยะทาง (ไม่หายไปทั้งหมด)",
  "แอนิเมชันบีคอน", "เปิดแอนิเมชันความสว่างที่เต้นช้าๆ บนลำแสง"],

"vi_vn": ["Tìm bất kỳ vật phẩm nào trong mọi rương chỉ trong nháy mắt. OmniChest bổ sung tìm kiếm toàn mạng lưới với các ghim trong thế giới, gửi đồ thông minh, sắp xếp theo danh mục, khóa ô và mẫu rương tái sử dụng — một bộ công cụ để giữ kho gọn gàng.",
  "Đặt lại tất cả thay đổi?", "Có", "Không", "Tất cả thiết lập đang ở giá trị mặc định.",
  "Hiệu ứng đèn hiệu",
  "Bật hiệu ứng đèn hiệu", "Vẽ một chùm sáng trong suốt kiểu đèn hiệu bốc lên từ mỗi ghim tìm kiếm để thấy được từ xa. Bản thân ghim không đổi.",
  "Độ mờ đèn hiệu", "Độ mờ cơ bản của chùm sáng. Kết hợp với mờ dần và nhịp đập.",
  "Bề rộng đèn hiệu", "Bề rộng lõi chùm sáng (khối). Ánh sáng bên ngoài rộng hơn.",
  "Mờ theo khoảng cách", "Làm mờ dần chùm sáng theo khoảng cách (không bao giờ biến mất hẳn).",
  "Hoạt ảnh đèn hiệu", "Bật hoạt ảnh độ sáng nhấp nháy chậm trên chùm sáng."],

"pl_pl": ["Znajdź dowolny przedmiot we wszystkich skrzyniach jednym spojrzeniem. OmniChest dodaje wyszukiwanie w całej sieci skrzyń ze znacznikami w świecie, inteligentne odkładanie, sortowanie według kategorii, blokady slotów i wielokrotnego użytku szablony skrzyń — jeden zestaw narzędzi do porządku w magazynie.",
  "Na pewno zresetować wszystkie zmiany?", "Tak", "Nie", "Wszystkie ustawienia mają wartości domyślne.",
  "Efekt sygnalizatora",
  "Włącz efekt sygnalizatora", "Rysuje półprzezroczystą wiązkę w stylu sygnalizatora unoszącą się z każdego znacznika wyszukiwania, by był widoczny z daleka. Same znaczniki się nie zmieniają.",
  "Krycie sygnalizatora", "Podstawowe krycie wiązki. Łączy się z zanikaniem i pulsowaniem.",
  "Szerokość sygnalizatora", "Szerokość rdzenia wiązki w blokach. Zewnętrzna poświata jest szersza.",
  "Zanikanie z odległością", "Stopniowo przygasza wiązkę wraz z odległością (nigdy nie znika całkowicie).",
  "Animacja sygnalizatora", "Włącza powolną animację pulsującej jasności wiązki."],

"nl_nl": ["Vind elk item in al je kisten in één oogopslag. OmniChest voegt netwerkbreed zoeken toe met markeringen in de wereld, plus slim opbergen, sorteren op categorie, slotvergrendeling en herbruikbare kistsjablonen — één gereedschapskist om je opslag netjes te houden.",
  "Echt alle wijzigingen resetten?", "Ja", "Nee", "Alle instellingen staan op standaardwaarden.",
  "Bakeneffect",
  "Bakeneffect inschakelen", "Tekent een doorschijnende bakenstraal die vanaf elke zoekmarkering omhoog gaat, zodat hij van veraf zichtbaar blijft. De markeringen zelf veranderen niet.",
  "Bakendekking", "Basisdekking van de straal. Wordt gecombineerd met vervaging en puls.",
  "Bakenbreedte", "Breedte van de straalkern in blokken. De buitenste gloed is breder.",
  "Afstandsvervaging", "Vervaagt de straal geleidelijk met de afstand (verdwijnt nooit helemaal).",
  "Bakenanimatie", "Schakelt een langzame pulserende helderheidsanimatie op de straal in."],

"sv_se": ["Hitta vilket föremål som helst i alla dina kistor med en blick. OmniChest lägger till nätverksbred sökning med markörer i världen, plus smart insättning, kategorisortering, fackslås och återanvändbara kistmallar — ett verktyg för att hålla förrådet i ordning.",
  "Vill du verkligen återställa alla ändringar?", "Ja", "Nej", "Alla inställningar har standardvärden.",
  "Fyrljuseffekt",
  "Aktivera fyrljuseffekt", "Ritar en genomskinlig fyrliknande stråle som stiger från varje sökmarkör så att den syns på avstånd. Markörerna själva ändras inte.",
  "Strålens opacitet", "Strålens grundopacitet. Kombineras med uttoning och puls.",
  "Strålbredd", "Bredden på strålens kärna i block. Det yttre skenet är bredare.",
  "Avståndstoning", "Tonar gradvis ner strålen med avståndet (försvinner aldrig helt).",
  "Fyranimering", "Aktiverar en långsam pulserande ljusstyrkeanimering på strålen."],

"da_dk": ["Find enhver genstand i alle dine kister med et enkelt blik. OmniChest tilføjer netværksbred søgning med markører i verdenen, smart aflevering, kategorisortering, pladslåse og genanvendelige kisteskabeloner — ét værktøj til at holde dit lager i orden.",
  "Vil du virkelig nulstille alle ændringer?", "Ja", "Nej", "Alle indstillinger er på standardværdier.",
  "Pejlemærke-effekt",
  "Aktivér pejlemærke-effekt", "Tegner en gennemsigtig pejlemærke-stråle, der stiger op fra hver søgemarkør, så den ses på afstand. Selve markørerne ændres ikke.",
  "Strålens uigennemsigtighed", "Strålens grunduigennemsigtighed. Kombineres med udtoning og puls.",
  "Strålebredde", "Bredden på strålens kerne i blokke. Det ydre skær er bredere.",
  "Afstandsudtoning", "Dæmper strålen gradvist med afstanden (forsvinder aldrig helt).",
  "Pejlemærke-animation", "Aktiverer en langsom pulserende lysstyrkeanimation på strålen."],

"nb_no": ["Finn hvilken som helst gjenstand i alle kistene dine med et øyekast. OmniChest legger til nettverksbredt søk med markører i verden, smart innsetting, kategorisortering, plasslåser og gjenbrukbare kistemaler — ett verktøy for å holde lageret ryddig.",
  "Vil du virkelig tilbakestille alle endringer?", "Ja", "Nei", "Alle innstillinger er på standardverdier.",
  "Fyrlykt-effekt",
  "Aktiver fyrlykt-effekt", "Tegner en gjennomsiktig fyrlykt-stråle som stiger fra hver søkemarkør, så den synes på avstand. Selve markørene endres ikke.",
  "Strålens tetthet", "Strålens grunntetthet. Kombineres med uttoning og puls.",
  "Strålebredde", "Bredden på strålekjernen i blokker. Den ytre gløden er bredere.",
  "Avstandsuttoning", "Demper strålen gradvis med avstanden (forsvinner aldri helt).",
  "Fyrlykt-animasjon", "Aktiverer en langsom pulserende lysstyrkeanimasjon på strålen."],

"fi_fi": ["Löydä mikä tahansa esine kaikista arkuistasi yhdellä silmäyksellä. OmniChest lisää verkonlaajuisen haun maailmaan asetettavilla merkeillä, älykkään tallettamisen, luokittelun, paikkalukot ja uudelleenkäytettävät arkkumallit — yksi työkalu varaston pitämiseen järjestyksessä.",
  "Nollataanko varmasti kaikki muutokset?", "Kyllä", "Ei", "Kaikki asetukset ovat oletusarvoissa.",
  "Majakkatehoste",
  "Ota majakkatehoste käyttöön", "Piirtää läpikuultavan majakkasäteen, joka nousee jokaisesta hakumerkistä, jotta se näkyy kaukaa. Merkit itse eivät muutu.",
  "Majakan peittävyys", "Säteen peruspeittävyys. Yhdistyy häivytykseen ja sykkeeseen.",
  "Majakan leveys", "Säteen ytimen leveys lohkoina. Ulompi hehku on leveämpi.",
  "Etäisyyshäivytys", "Himmentää sädettä vähitellen etäisyyden myötä (ei katoa koskaan kokonaan).",
  "Majakka-animaatio", "Ottaa käyttöön hitaan sykkivän kirkkausanimaation säteessä."],

"cs_cz": ["Najděte jakýkoli předmět ve všech truhlách jediným pohledem. OmniChest přidává vyhledávání napříč sítí truhel se značkami ve světě, chytré ukládání, třídění podle kategorií, zámky slotů a opakovaně použitelné šablony truhel — jeden nástroj pro pořádek ve skladu.",
  "Opravdu obnovit všechny změny?", "Ano", "Ne", "Všechna nastavení jsou na výchozích hodnotách.",
  "Efekt majáku",
  "Zapnout efekt majáku", "Vykreslí průsvitný paprsek jako maják stoupající z každé vyhledávací značky, aby byla vidět zdálky. Samotné značky se nemění.",
  "Krytí majáku", "Základní krytí paprsku. Kombinuje se s blednutím a pulzováním.",
  "Šířka majáku", "Šířka jádra paprsku v blocích. Vnější záře je širší.",
  "Blednutí podle vzdálenosti", "Postupně ztlumí paprsek se vzdáleností (nikdy zcela nezmizí).",
  "Animace majáku", "Zapne pomalou pulzující animaci jasu paprsku."],

"hu_hu": ["Találj meg bármilyen tárgyat az összes ládádban egyetlen pillantással. Az OmniChest hálózati keresést ad világbeli jelölőkkel, valamint okos lerakást, kategória szerinti rendezést, rekeszzárakat és újrahasználható láda-sablonokat — egyetlen eszköz a rendezett tárolásért.",
  "Biztosan visszaállítod az összes módosítást?", "Igen", "Nem", "Minden beállítás alapértelmezett értéken van.",
  "Jelzőfény-effekt",
  "Jelzőfény-effekt bekapcsolása", "Áttetsző, jelzőfényszerű sugarat rajzol minden keresési jelölőből felfelé, hogy messziről is látsszódjon. A jelölők maguk nem változnak.",
  "Jelzőfény átlátszatlansága", "A sugár alap átlátszatlansága. Az elhalványulással és lüktetéssel kombinálódik.",
  "Jelzőfény szélessége", "A sugármag szélessége blokkban. A külső ragyogás szélesebb.",
  "Távolsági elhalványulás", "Fokozatosan halványítja a sugarat a távolsággal (sosem tűnik el teljesen).",
  "Jelzőfény-animáció", "Lassú, lüktető fényerő-animációt kapcsol be a sugáron."],

"ro_ro": ["Găseşte orice obiect din toate cuferele tale dintr-o privire. OmniChest adaugă căutare în întreaga reţea cu marcaje în lume, depozitare inteligentă, sortare pe categorii, blocarea sloturilor şi şabloane de cufăr reutilizabile — un singur instrument pentru un depozit ordonat.",
  "Sigur resetezi toate modificările?", "Da", "Nu", "Toate setările sunt la valorile implicite.",
  "Efect de far",
  "Activează efectul de far", "Desenează un fascicul translucid în stil far care urcă din fiecare marcaj de căutare ca să fie vizibil de departe. Marcajele în sine nu se schimbă.",
  "Opacitatea farului", "Opacitatea de bază a fasciculului. Se combină cu estomparea şi pulsaţia.",
  "Lăţimea farului", "Lăţimea nucleului fasciculului în blocuri. Strălucirea exterioară e mai lată.",
  "Estompare cu distanţa", "Estompează treptat fasciculul cu distanţa (nu dispare niciodată complet).",
  "Animaţia farului", "Activează o animaţie lentă de luminozitate pulsatorie pe fascicul."],

"uk_ua": ["Знаходьте будь-який предмет у всіх скринях з першого погляду. OmniChest додає пошук по всій мережі скринь із позначками у світі, розумне складання, сортування за категоріями, блокування комірок і багаторазові шаблони скринь — єдиний інструмент для порядку у сховищі.",
  "Справді скинути всі зміни?", "Так", "Ні", "Усі налаштування мають типові значення.",
  "Ефект маяка",
  "Увімкнути ефект маяка", "Малює напівпрозорий промінь у стилі маяка, що піднімається від кожної позначки пошуку, щоб її було видно здалеку. Самі позначки не змінюються.",
  "Непрозорість маяка", "Базова непрозорість променя. Поєднується із згасанням і пульсацією.",
  "Ширина маяка", "Ширина ядра променя у блоках. Зовнішнє сяйво ширше.",
  "Згасання за відстанню", "Поступово приглушує промінь з відстанню (ніколи не зникає повністю).",
  "Анімація маяка", "Увімкає повільну пульсуючу анімацію яскравості променя."],

"id_id": ["Temukan item apa pun di semua petimu sekilas pandang. OmniChest menambah pencarian seluruh jaringan dengan penanda di dunia, penyimpanan pintar, penyortiran kategori, kunci slot, dan templat peti yang bisa dipakai ulang — satu alat untuk menjaga penyimpanan tetap rapi.",
  "Yakin atur ulang semua perubahan?", "Ya", "Tidak", "Semua pengaturan pada nilai bawaan.",
  "Efek mercusuar",
  "Aktifkan efek mercusuar", "Menggambar sinar tembus pandang mirip mercusuar yang naik dari tiap penanda pencarian agar terlihat dari jauh. Penanda itu sendiri tidak berubah.",
  "Opasitas mercusuar", "Opasitas dasar sinar. Digabung dengan pemudaran dan denyut.",
  "Lebar mercusuar", "Lebar inti sinar dalam blok. Cahaya luar lebih lebar.",
  "Pemudaran jarak", "Meredupkan sinar secara bertahap sesuai jarak (tidak pernah hilang sepenuhnya).",
  "Animasi mercusuar", "Mengaktifkan animasi kecerahan berdenyut lambat pada sinar."],

"ms_my": ["Cari apa-apa item dalam semua peti anda dengan sekali pandang. OmniChest menambah carian seluruh rangkaian dengan penanda dalam dunia, simpanan pintar, isihan mengikut kategori, kunci slot dan templat peti boleh guna semula — satu alat untuk memastikan stor kemas.",
  "Betul-betul tetapkan semula semua perubahan?", "Ya", "Tidak", "Semua tetapan pada nilai lalai.",
  "Kesan suar",
  "Dayakan kesan suar", "Melukis pancaran lut sinar seperti suar yang naik dari setiap penanda carian supaya kelihatan dari jauh. Penanda itu sendiri tidak berubah.",
  "Kelegapan suar", "Kelegapan asas pancaran. Digabung dengan lutsinar dan denyutan.",
  "Lebar suar", "Lebar teras pancaran dalam blok. Cahaya luar lebih lebar.",
  "Pudar jarak", "Memudarkan pancaran secara beransur dengan jarak (tidak pernah hilang sepenuhnya).",
  "Animasi suar", "Mendayakan animasi kecerahan berdenyut perlahan pada pancaran."],
}


def insert_keys(path: pathlib.Path, mapping: dict):
    text = path.read_text(encoding="utf-8")
    if "modmenu.descriptionTranslation.omnichest" in text:
        return "skip (already present)"
    idx = text.rfind("}")
    if idx < 0:
        return "ERROR: no closing brace"
    head = text[:idx].rstrip()
    if not head.endswith(","):
        head += ","
    lines = []
    for k in KEY_ORDER:
        v = mapping[k]
        lines.append("  " + json.dumps(k, ensure_ascii=False) + ": " + json.dumps(v, ensure_ascii=False))
    block = ",\n".join(lines)
    new_text = head + "\n" + block + "\n}\n"
    # 妥当性チェック。
    json.loads(new_text)
    path.write_text(new_text, encoding="utf-8")
    return "updated"


def main():
    results = {}
    for lang, vals in T.items():
        path = LANG_DIR / (lang + ".json")
        if not path.exists():
            results[lang] = "MISSING FILE"
            continue
        mapping = dict(zip(KEY_ORDER, vals))
        if len(vals) != len(KEY_ORDER):
            results[lang] = "ERROR: value count %d != %d" % (len(vals), len(KEY_ORDER))
            continue
        results[lang] = insert_keys(path, mapping)
    for lang in sorted(results):
        print("%-7s %s" % (lang, results[lang]))
    # 全 lang ファイルが対象になったか確認。
    missing = [p.stem for p in LANG_DIR.glob("*.json") if p.stem not in T]
    if missing:
        print("NOT IN TABLE:", missing)


if __name__ == "__main__":
    main()
