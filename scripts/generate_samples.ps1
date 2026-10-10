# Lumina Library - Sample PDF Generator
# Generates valid standard PDF 1.4 documents for classic public domain books

param (
    [string]$OutputDir = "src/main/resources/static/samples"
)

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}

function New-SamplePdf {
    param (
        [string]$Filename,
        [string]$Title,
        [string]$Author,
        [string]$Publisher,
        [string]$Year,
        [string]$Category,
        [string]$Synopsis,
        [string[]]$KeyThemes,
        [string[]]$ChapterExcerpts
    )

    $outPath = Join-Path $OutputDir $Filename
    $ms = New-Object System.IO.MemoryStream

    function Write-Ascii($str) {
        $bytes = [System.Text.Encoding]::GetEncoding("ISO-8859-1").GetBytes($str)
        $ms.Write($bytes, 0, $bytes.Length)
    }

    function Escape-Pdf($str) {
        if (-not $str) { return "" }
        return $str.Replace("\", "\\").Replace("(", "\(").Replace(")", "\)") -replace '[^\x20-\x7E]', ' '
    }

    $offsets = New-Object System.Collections.Generic.List[long]

    # Header
    Write-Ascii "%PDF-1.4`n%`xE2`xE3`xCF`xD3`n"

    # Obj 1: Catalog
    $offsets.Add($ms.Position)
    Write-Ascii "1 0 obj`n<< /Type /Catalog /Pages 2 0 R >>`nendobj`n"

    # Obj 2: Pages
    $offsets.Add($ms.Position)
    Write-Ascii "2 0 obj`n<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>`nendobj`n"

    # Obj 3: Page 1
    $offsets.Add($ms.Position)
    Write-Ascii "3 0 obj`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 5 0 R /Resources << /Font << /F1 7 0 R /F2 8 0 R >> >> >>`nendobj`n"

    # Obj 4: Page 2
    $offsets.Add($ms.Position)
    Write-Ascii "4 0 obj`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 6 0 R /Resources << /Font << /F1 7 0 R /F2 8 0 R >> >> >>`nendobj`n"

    # Content Stream Page 1
    $sb1 = New-Object System.Text.StringBuilder
    $sb1.Append("0.08 0.12 0.22 rg 0 750 595 92 re f`n") | Out-Null
    $sb1.Append("0.13 0.77 0.37 rg 0 746 595 4 re f`n") | Out-Null
    $sb1.Append("BT /F1 16 Tf 1 1 1 rg 40 790 Td (LUMINA DIGITAL LIBRARY - VERIFIED MASTERPIECE) Tj ET`n") | Out-Null
    $sb1.Append("BT /F2 10 Tf 0.8 0.85 0.9 rg 40 768 Td (Standard Public Domain Digital Volume | Reader Ready) Tj ET`n") | Out-Null

    $sb1.Append("BT /F1 22 Tf 0.08 0.12 0.22 rg 40 705 Td (" + (Escape-Pdf $Title) + ") Tj ET`n") | Out-Null
    $sb1.Append("BT /F2 12 Tf 0.13 0.77 0.37 rg 40 684 Td (Author: " + (Escape-Pdf $Author) + " | Published: " + (Escape-Pdf $Year) + ") Tj ET`n") | Out-Null

    $sb1.Append("0.96 0.97 0.99 rg 40 600 515 65 re f`n") | Out-Null
    $sb1.Append("0.8 0.85 0.9 rg 1 w 40 600 515 65 re S`n") | Out-Null
    $sb1.Append("BT /F1 10 Tf 0.2 0.25 0.3 rg 55 645 Td (CATEGORY:) Tj ET`n") | Out-Null
    $sb1.Append("BT /F2 10 Tf 0.1 0.1 0.1 rg 130 645 Td (" + (Escape-Pdf $Category) + ") Tj ET`n") | Out-Null
    $sb1.Append("BT /F1 10 Tf 0.2 0.25 0.3 rg 300 645 Td (PUBLISHER:) Tj ET`n") | Out-Null
    $sb1.Append("BT /F2 10 Tf 0.1 0.1 0.1 rg 380 645 Td (" + (Escape-Pdf $Publisher) + ") Tj ET`n") | Out-Null
    $sb1.Append("BT /F1 10 Tf 0.2 0.25 0.3 rg 55 620 Td (VERIFICATION:) Tj ET`n") | Out-Null
    $sb1.Append("BT /F2 10 Tf 0.08 0.6 0.2 rg 130 620 Td (Complete Full Text Available) Tj ET`n") | Out-Null

    $sb1.Append("BT /F1 14 Tf 0.08 0.12 0.22 rg 40 560 Td (1. Volume Overview & Critical Introduction) Tj ET`n") | Out-Null
    $sb1.Append("0.13 0.77 0.37 rg 40 552 515 1.5 re f`n") | Out-Null

    $words = $Synopsis.Split(' ')
    $lines = New-Object System.Collections.Generic.List[string]
    $curr = ""
    foreach ($w in $words) {
        if (($curr.Length + $w.Length + 1) -gt 76) {
            $lines.Add($curr)
            $curr = $w
        } else {
            if ($curr.Length -gt 0) { $curr += " " }
            $curr += $w
        }
    }
    if ($curr.Length -gt 0) { $lines.Add($curr) }

    $y = 530
    $sb1.Append("BT /F2 11 Tf 0.2 0.2 0.2 rg ") | Out-Null
    foreach ($l in $lines) {
        $sb1.Append("40 $y Td (" + (Escape-Pdf $l) + ") Tj ") | Out-Null
        $y -= 16
        if ($y -lt 380) { break }
    }
    $sb1.Append("ET`n") | Out-Null

    # Key Themes
    $sb1.Append("BT /F1 14 Tf 0.08 0.12 0.22 rg 40 350 Td (2. Core Themes and Analytical Takeaways) Tj ET`n") | Out-Null
    $sb1.Append("0.13 0.77 0.37 rg 40 342 515 1.5 re f`n") | Out-Null

    $ty = 320
    foreach ($theme in $KeyThemes) {
        $sb1.Append("0.13 0.77 0.37 rg 45 $($ty + 3) 5 5 re f`n") | Out-Null
        $sb1.Append("BT /F2 10 Tf 0.15 0.15 0.15 rg 58 $ty Td (" + (Escape-Pdf $theme) + ") Tj ET`n") | Out-Null
        $ty -= 24
    }

    # Footer
    $sb1.Append("0.8 0.85 0.9 rg 40 50 515 1 re f`n") | Out-Null
    $sb1.Append("BT /F2 9 Tf 0.5 0.55 0.6 rg 40 35 Td (Page 1 of 2 | Lumina Online Library | Public Edition) Tj ET`n") | Out-Null

    $c1 = [System.Text.Encoding]::GetEncoding("ISO-8859-1").GetBytes($sb1.ToString())
    $offsets.Add($ms.Position)
    Write-Ascii "5 0 obj`n<< /Length $($c1.Length) >>`nstream`n"
    $ms.Write($c1, 0, $c1.Length)
    Write-Ascii "`nendstream`nendobj`n"

    # Content Stream Page 2: Chapter Excerpts and Study Notes
    $sb2 = New-Object System.Text.StringBuilder
    $sb2.Append("0.08 0.12 0.22 rg 0 780 595 62 re f`n") | Out-Null
    $sb2.Append("0.13 0.77 0.37 rg 0 776 595 4 re f`n") | Out-Null
    $sb2.Append("BT /F1 14 Tf 1 1 1 rg 40 800 Td (CHAPTER EXCERPTS & STUDY NOTES - " + (Escape-Pdf $Title) + ") Tj ET`n") | Out-Null

    $sb2.Append("BT /F1 13 Tf 0.08 0.12 0.22 rg 40 740 Td (Notable Passages & Original Text Highlights) Tj ET`n") | Out-Null
    $sb2.Append("0.13 0.77 0.37 rg 40 732 515 1.5 re f`n") | Out-Null

    $ey = 705
    foreach ($excerpt in $ChapterExcerpts) {
        $sb2.Append("0.96 0.97 0.99 rg 40 $($ey - 25) 515 40 re f`n") | Out-Null
        $sb2.Append("0.85 0.88 0.92 rg 1 w 40 $($ey - 25) 515 40 re S`n") | Out-Null
        $sb2.Append("BT /F2 10 Tf 0.2 0.25 0.3 rg 50 $($ey - 5) Td (" + (Escape-Pdf $excerpt) + ") Tj ET`n") | Out-Null
        $ey -= 55
    }

    $sb2.Append("BT /F1 13 Tf 0.08 0.12 0.22 rg 40 460 Td (Interactive Reading Notes and Personal Margin Space) Tj ET`n") | Out-Null
    $sb2.Append("0.13 0.77 0.37 rg 40 452 515 1.5 re f`n") | Out-Null

    $lineY = 425
    for ($i = 0; $i -lt 12; $i++) {
        $sb2.Append("0.85 0.88 0.92 rg 40 $lineY 515 1 re f`n") | Out-Null
        $lineY -= 30
    }

    # Footer
    $sb2.Append("0.8 0.85 0.9 rg 40 50 515 1 re f`n") | Out-Null
    $sb2.Append("BT /F2 9 Tf 0.5 0.55 0.6 rg 40 35 Td (Page 2 of 2 | Lumina Online Library | End of Volume) Tj ET`n") | Out-Null

    $c2 = [System.Text.Encoding]::GetEncoding("ISO-8859-1").GetBytes($sb2.ToString())
    $offsets.Add($ms.Position)
    Write-Ascii "6 0 obj`n<< /Length $($c2.Length) >>`nstream`n"
    $ms.Write($c2, 0, $c2.Length)
    Write-Ascii "`nendstream`nendobj`n"

    # Font Objects
    $offsets.Add($ms.Position)
    Write-Ascii "7 0 obj`n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>`nendobj`n"
    $offsets.Add($ms.Position)
    Write-Ascii "8 0 obj`n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>`nendobj`n"

    # Xref
    $xrefPos = $ms.Position
    Write-Ascii "xref`n0 $($offsets.Count + 1)`n0000000000 65535 f `n"
    foreach ($off in $offsets) {
        Write-Ascii ("{0:D10} 00000 n `n" -f $off)
    }

    # Trailer
    Write-Ascii "trailer`n<< /Size $($offsets.Count + 1) /Root 1 0 R >>`nstartxref`n$xrefPos`n%%EOF`n"

    [System.IO.File]::WriteAllBytes($outPath, $ms.ToArray())
    Write-Host "Generated: $outPath ($($ms.Length) bytes)"
}

# 1. The Art of War
New-SamplePdf -Filename "the-art-of-war.pdf" `
    -Title "The Art of War" `
    -Author "Sun Tzu" `
    -Publisher "Classic Chinese Literature" `
    -Year "5th Century BC" `
    -Category "Philosophy and Military Strategy" `
    -Synopsis "The Art of War is an ancient military treatise dating from the Late Spring and Autumn Period. Attributed to the ancient strategist Sun Tzu, the text is composed of 13 chapters, each devoted to one aspect of strategy and tactics. It remains one of the most widely read and influential treatises on leadership and conflict resolution in world history." `
    -KeyThemes @(
        "Supreme excellence consists of breaking the enemy's resistance without fighting.",
        "Knowing yourself and knowing the other party guarantees unbroken victory.",
        "Speed, adaptability, and positioning triumph over raw mass in all engagements.",
        "Preparation and deceptive alignment allow leaders to command the field of action."
    ) `
    -ChapterExcerpts @(
        "Chapter I: Laying Plans - The art of war is of vital importance to the State.",
        "Chapter III: Attack by Stratagem - In the practical art of war, the best thing of all is to take the enemy's country whole.",
        "Chapter VI: Weak Points and Strong - Appear at points which the enemy must hasten to defend; march swiftly to where you are not expected."
    )

# 2. Frankenstein
New-SamplePdf -Filename "frankenstein.pdf" `
    -Title "Frankenstein; or, The Modern Prometheus" `
    -Author "Mary Shelley" `
    -Publisher "Lackington, Hughes, Harding & Jones" `
    -Year "1818" `
    -Category "Gothic Horror and Science Fiction" `
    -Synopsis "Frankenstein tells the gripping story of Victor Frankenstein, a gifted young scientist whose obsessive quest to conquer mortality leads him to animate lifeless matter. Shelley's masterpiece combines elements of gothic fiction and Romantic philosophy, serving as a pioneer of the science-fiction genre and an enduring inquiry into the ethical boundaries of human knowledge." `
    -KeyThemes @(
        "The moral and existential consequences of uncontrolled scientific ambition.",
        "The destructive isolation caused by society's rejection of the unfamiliar.",
        "Parental and creator responsibility toward one's progeny and creations.",
        "Nature as both a sublime sanctuary and an unyielding force of reckoning."
    ) `
    -ChapterExcerpts @(
        "Letter IV: I will pioneer a new way, explore unknown powers, and unfold to the world the deepest mysteries of creation.",
        "Chapter 5: It was on a dreary night of November that I beheld the accomplishment of my toils. With an anxiety that almost amounted to agony...",
        "Chapter 10: Remember that I am thy creature; I ought to be thy Adam, but I am rather the fallen angel, whom thou drivest from joy."
    )

# 3. Sherlock Holmes
New-SamplePdf -Filename "sherlock-holmes.pdf" `
    -Title "The Adventures of Sherlock Holmes" `
    -Author "Arthur Conan Doyle" `
    -Publisher "The Strand Magazine / George Newnes" `
    -Year "1892" `
    -Category "Detective Fiction and Mystery" `
    -Synopsis "The Adventures of Sherlock Holmes gathers twelve iconic short stories chronicling the casework of consulting detective Sherlock Holmes and his steadfast partner Dr. John Watson. Set amidst Victorian and Edwardian London, these tales illustrate the power of observation, logical deduction, forensic precision, and psychological acumen in unraveling the most baffling crimes." `
    -KeyThemes @(
        "The art of deductive reasoning: 'When you have eliminated the impossible, whatever remains, however improbable, must be the truth.'",
        "The contrast between observation and mere sight in human everyday perception.",
        "Justice vs. legality: Holmes frequently navigates morality above rigid bureaucracy.",
        "The enduring friendship and synergy between analytical intellect and empathetic humanity."
    ) `
    -ChapterExcerpts @(
        "A Scandal in Bohemia: To Sherlock Holmes she is always THE woman. I have seldom heard him mention her under any other name.",
        "The Red-Headed League: It is quite a three-pipe problem, and I beg that you won't speak to me for fifty minutes.",
        "The Speckled Band: Violence does, in truth, recoil upon the violent, and the schemer falls into the pit which he digs for another."
    )

# 4. Pride and Prejudice
New-SamplePdf -Filename "pride-and-prejudice.pdf" `
    -Title "Pride and Prejudice" `
    -Author "Jane Austen" `
    -Publisher "T. Egerton, Whitehall" `
    -Year "1813" `
    -Category "Romantic Fiction and Social Satire" `
    -Synopsis "Jane Austen's celebrated novel follows the spirited and perceptive Elizabeth Bennet as she confronts societal expectations, familial pressures, and initial misconceptions surrounding the aristocratic and enigmatic Mr. Fitzwilliam Darcy. Austen weaves an incisive critique of Regency-era social class and economic dependencies through sharp dialogue and unforgettable character development." `
    -KeyThemes @(
        "The danger of quick first impressions and premature judgments.",
        "The interplay of pride, vulnerability, and mutual self-discovery.",
        "Economic autonomy vs. social convention for 19th-century women.",
        "Wit, irony, and dialogue as tools of moral clarity and social insight."
    ) `
    -ChapterExcerpts @(
        "Chapter 1: It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.",
        "Chapter 34: In vain have I struggled. It will not do. My feelings will not be repressed. You must allow me to tell you how ardently I admire and love you.",
        "Chapter 58: Till this moment I never knew myself."
    )

# 5. Alice's Adventures in Wonderland
New-SamplePdf -Filename "alice-in-wonderland.pdf" `
    -Title "Alice's Adventures in Wonderland" `
    -Author "Lewis Carroll" `
    -Publisher "Macmillan and Co." `
    -Year "1865" `
    -Category "Fantasy and Literary Nonsense" `
    -Synopsis "Alice's Adventures in Wonderland narrates the fantastical journey of a young girl named Alice who tumbles down a rabbit hole into a bizarre dreamscape filled with anthropomorphic animals, riddle-posing caterpillars, mad tea parties, and eccentric monarchs. Carroll's work is celebrated worldwide for its playful subversion of rigid Victorian etiquette and ingenious linguistic wordplay." `
    -KeyThemes @(
        "The curiosity of childhood navigating the arbitrary logic of adulthood.",
        "Linguistic wordplay, puns, paradoxes, and the limits of structured language.",
        "Identity and personal growth through perpetual metamorphosis.",
        "The absurdity of bureaucratic authority epitomized by the Queen of Hearts."
    ) `
    -ChapterExcerpts @(
        "Down the Rabbit-Hole: 'Curiouser and curiouser!' cried Alice (she was so much surprised, that for the moment she quite forgot how to speak good English).",
        "A Mad Tea-Party: 'Why is a raven like a writing-desk?' 'Have you guessed the riddle yet?' the Hatter asked...",
        "The Queen's Croquet-Ground: 'Off with their heads!' shouted the Queen of Hearts at the top of her voice."
    )
