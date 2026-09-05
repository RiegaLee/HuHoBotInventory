param(
    [string]$AuditRoot = "data/visual-audit/26.1.2-B1B315857266-MB7-PD1337875",
    [string]$ClientJar = (Join-Path $env:APPDATA ".minecraft/versions/26.1.2/26.1.2.jar")
)

$ErrorActionPreference = "Stop"
$auditPath = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $AuditRoot))
$fullAudit = Join-Path $auditPath "reports/full-item-render-audit.tsv"
$inventoryOutput = Join-Path $auditPath "special-renderer-inventory.tsv"
$priorityOutput = Join-Path $auditPath "special-renderer-priority.tsv"
$clientJarPath = [System.IO.Path]::GetFullPath($ClientJar)

foreach ($required in @($fullAudit, $clientJarPath)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing analysis input: $required"
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($clientJarPath)
try {
    function Read-ClientItemJson([string]$itemId) {
        $name = $itemId.Substring($itemId.IndexOf(':') + 1)
        $entryName = "assets/minecraft/items/$name.json"
        $entry = $zip.GetEntry($entryName)
        if ($null -eq $entry) { throw "Client item definition missing: $entryName" }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try {
            return [pscustomobject]@{
                Path = $entryName
                Json = ($reader.ReadToEnd() | ConvertFrom-Json -Depth 100)
            }
        } finally {
            $reader.Dispose()
        }
    }

    function New-InventoryRow(
        [string]$ItemId,
        [string]$GroupId,
        [string]$SpecialType,
        [string]$Renderer,
        [string]$Geometry,
        [string]$Texture,
        [string]$Transform,
        [string]$Dependency,
        [string]$Strategy,
        [string]$Cost,
        [string]$Value,
        [string]$Priority,
        [int]$Affected,
        [string]$Evidence
    ) {
        $definition = Read-ClientItemJson $ItemId
        return [pscustomobject][ordered]@{
            item_id = $ItemId
            audit_bucket = "SPECIAL_RENDERER"
            group_id = $GroupId
            special_type = $SpecialType
            client_item_model = $definition.Path
            renderer_class_or_type = $Renderer
            geometry_source = $Geometry
            texture_source = $Texture
            gui_transform = $Transform
            dynamic_state_dependency = $Dependency
            suggested_strategy = $Strategy
            implementation_cost = $Cost
            player_usage_value = $Value
            priority = $Priority
            items_affected_per_renderer = $Affected
            evidence = $Evidence
        }
    }

    $auditRows = Import-Csv -LiteralPath $fullAudit -Delimiter "`t" -Encoding UTF8
    $specialIds = @($auditRows | Where-Object issue_bucket -eq "SPECIAL_RENDERER" | Select-Object -ExpandProperty item_id)
    $dynamicIds = @($auditRows | Where-Object issue_bucket -eq "DYNAMIC_ITEM" | Select-Object -ExpandProperty item_id)
    if ($specialIds.Count -ne 56) { throw "Expected 56 SPECIAL_RENDERER rows, found $($specialIds.Count)" }
    if ($dynamicIds.Count -ne 32) { throw "Expected 32 DYNAMIC_ITEM rows, found $($dynamicIds.Count)" }
    if (@($specialIds | Where-Object { $_ -in $dynamicIds }).Count -ne 0) {
        throw "Audit buckets must be disjoint"
    }

    $rows = [System.Collections.Generic.List[object]]::new()
    foreach ($itemId in $specialIds) {
        $name = $itemId.Substring($itemId.IndexOf(':') + 1)
        if ($name -match '^(black|blue|brown|cyan|gray|green|light_blue|light_gray|lime|magenta|orange|pink|purple|red|white|yellow)_bed$') {
            $color = $Matches[1]
            $rows.Add((New-InventoryRow $itemId "BED_FAMILY" "minecraft:bed (head + foot composite)" "BedSpecialRenderer (NoDataSpecialModelRenderer)" "BedRenderer head/foot geometry" "VANILLA_CLIENT:entity/bed/$color.png (Faithful 26.2 has no entity/bed override)" "template_bed GUI [30,160,0], translate [2,3,0], scale .5325; item composite supplies fixed head/foot transforms" "NONE; color is fixed by item definition" "STATIC_EQUIVALENT_CANDIDATE" "MEDIUM" "HIGH" "B" 16 "Two fixed minecraft:special bed nodes; no ItemStack argument"))
            continue
        }
        if ($name -eq 'shulker_box' -or $name -match '_shulker_box$') {
            $texture = if ($name -eq 'shulker_box') { 'shulker' } else { 'shulker_' + $name.Substring(0, $name.Length - '_shulker_box'.Length) }
            $rows.Add((New-InventoryRow $itemId "SHULKER_FAMILY" "minecraft:shulker_box" "ShulkerBoxSpecialRenderer (NoDataSpecialModelRenderer)" "ShulkerBoxRenderer fixed closed model" "RESOURCE_PACK:entity/shulker/$texture.png" "template_shulker_box GUI [30,45,0], scale .625; wrapper translate [.5,1.4995,.5], scale .9995" "NONE; item definition fixes texture and default closed openness" "STATIC_EQUIVALENT_CANDIDATE" "LOW" "HIGH" "A" 17 "One geometry/transform; 17 texture variants"))
            continue
        }
        if ($name -eq 'ender_chest' -or $name -match '^(waxed_)?(exposed_|weathered_|oxidized_)?copper_chest$') {
            $texture = switch -Regex ($name) {
                '^ender_chest$' { 'ender'; break }
                'exposed' { 'copper_exposed'; break }
                'weathered' { 'copper_weathered'; break }
                'oxidized' { 'copper_oxidized'; break }
                default { 'copper' }
            }
            $rows.Add((New-InventoryRow $itemId "CHEST_STATIC_FAMILY" "minecraft:chest" "ChestSpecialRenderer (NoDataSpecialModelRenderer)" "ChestModel bottom/lid/lock" "RESOURCE_PACK:entity/chest/$texture.png" "template_chest GUI [30,45,0], translate [0,0,0], scale .625" "NONE; waxed variants intentionally share oxidation texture" "STATIC_EQUIVALENT_CANDIDATE" "LOW" "HIGH" "A" 9 "Existing client-equivalent Chest generator can reuse geometry/UV/projection and change texture"))
            continue
        }
        if ($name -eq 'trapped_chest') {
            $rows.Add((New-InventoryRow $itemId "TRAPPED_CHEST_SEASONAL" "minecraft:chest under minecraft:local_time select" "ChestSpecialRenderer (NoDataSpecialModelRenderer)" "ChestModel bottom/lid/lock" "RESOURCE_PACK:entity/chest/trapped.png or christmas.png" "template_chest GUI [30,45,0], translate [0,0,0], scale .625" "LOCAL_TIME MM-dd; christmas on 12-24..12-26" "LIGHTWEIGHT_SPECIAL_RENDERER" "LOW" "HIGH" "A" 1 "Reuse Chest generator; runtime only selects one of two pre-baked textures"))
            continue
        }
        if ($name -match 'copper_golem_statue$') {
            $texture = switch -Regex ($name) {
                'exposed' { 'copper_golem_exposed.png'; break }
                'weathered' { 'copper_golem_weathered.png'; break }
                'oxidized' { 'copper_golem_oxidized.png'; break }
                default { 'copper_golem.png' }
            }
            $rows.Add((New-InventoryRow $itemId "COPPER_GOLEM_STATUES" "minecraft:copper_golem_statue under minecraft:block_state select" "CopperGolemStatueSpecialRenderer (NoDataSpecialModelRenderer per selected pose)" "CopperGolemStatueModel: standing/sitting/running/star" "RESOURCE_PACK:entity/copper_golem/$texture" "template_copper_golem_statue GUI [30,45,180], translate [0,2,0], scale .55" "ITEM BLOCK_STATE component: copper_golem_pose; four poses" "LIGHTWEIGHT_SPECIAL_RENDERER" "MEDIUM" "LOW" "C" 8 "One renderer covers 8 oxidation/wax variants and 4 pose choices"))
            continue
        }
        switch ($name) {
            'air' {
                $rows.Add((New-InventoryRow $itemId "AIR_NON_ITEM" "NONE; minecraft:model item/air" "NONE" "No visible item geometry" "NONE" "NONE" "NONE; air represents an empty slot" "DEFERRED_DYNAMIC" "LOW" "LOW" "DEFERRED" 1 "Audit classification false positive; air must remain visually empty, not receive an icon"))
            }
            'conduit' {
                $rows.Add((New-InventoryRow $itemId "CONDUIT_STATIC" "minecraft:conduit" "ConduitSpecialRenderer (NoDataSpecialModelRenderer)" "Conduit shell ModelPart" "RESOURCE_PACK:entity/conduit/base.png" "item/conduit GUI [30,45,0], scale 1.0; wrapper translate [.5,.5,.5]" "NONE; item uses only the fixed shell" "STATIC_EQUIVALENT_CANDIDATE" "MEDIUM" "MEDIUM" "C" 1 "Client item renderer is fixed and does not animate cage/wind/eye"))
            }
            'decorated_pot' {
                $rows.Add((New-InventoryRow $itemId "DECORATED_POT" "minecraft:decorated_pot" "DecoratedPotSpecialRenderer<PotDecorations>" "DecoratedPotRenderer base + four side faces" "RESOURCE_PACK:entity/decorated_pot/decorated_pot_base.png + pottery pattern textures" "item/decorated_pot GUI [30,45,0], scale .60" "POT_DECORATIONS ItemStack component; four sherd/pattern sides" "LIGHTWEIGHT_SPECIAL_RENDERER" "MEDIUM" "MEDIUM" "C" 1 "Requires descriptor/cache key and four-side texture selection"))
            }
            'leather_horse_armor' {
                $rows.Add((New-InventoryRow $itemId "LEATHER_HORSE_ARMOR" "NONE; minecraft:model with minecraft:dye tint" "Generated 2D model + Dye tint source" "item/generated two texture layers" "RESOURCE_PACK:item/leather_horse_armor.png + overlay.png" "Standard item/generated GUI transform" "DYED_COLOR ItemStack component" "LIGHTWEIGHT_SPECIAL_RENDERER" "LOW" "LOW" "C" 1 "Audit classification false positive; reuse existing dye-aware composition concepts"))
            }
            'trident' {
                $rows.Add((New-InventoryRow $itemId "TRIDENT_GUI" "GUI branch is minecraft:model; held fallback is minecraft:trident special" "Generated 2D model in GUI; TridentSpecialRenderer only outside GUI" "item/generated item/trident" "RESOURCE_PACK:item/trident.png" "minecraft:display_context select chooses item/trident for gui/ground/fixed/on_shelf" "DISPLAY_CONTEXT only; Inventory GUI branch is fixed" "STATIC_EQUIVALENT_CANDIDATE" "LOW" "HIGH" "A" 1 "Audit resolver stopped at nested special fallback instead of selecting the GUI case"))
            }
            default { throw "Unclassified SPECIAL_RENDERER item: $itemId" }
        }
    }
    if ($rows.Count -ne 56) { throw "Expected 56 detailed rows, found $($rows.Count)" }
    $rows | Export-Csv -LiteralPath $inventoryOutput -Delimiter "`t" -NoTypeInformation -Encoding UTF8

    $priority = @(
        [pscustomobject]@{priority='A';group_id='CHEST_STATIC_FAMILY';audit_bucket='SPECIAL_RENDERER';client_type='minecraft:chest';items_affected=9;strategy='STATIC_EQUIVALENT_CANDIDATE';cost='LOW';value='HIGH';predicted_pass_gain=9;recommendation='Reuse the accepted Chest geometry/UV/slot projection with ender and four copper oxidation textures; waxed variants share textures.'},
        [pscustomobject]@{priority='A';group_id='TRAPPED_CHEST_SEASONAL';audit_bucket='SPECIAL_RENDERER';client_type='minecraft:chest + local_time';items_affected=1;strategy='LIGHTWEIGHT_SPECIAL_RENDERER';cost='LOW';value='HIGH';predicted_pass_gain=1;recommendation='Pre-bake trapped and Christmas variants; choose by local MM-dd at runtime.'},
        [pscustomobject]@{priority='A';group_id='SHULKER_FAMILY';audit_bucket='SPECIAL_RENDERER';client_type='minecraft:shulker_box';items_affected=17;strategy='STATIC_EQUIVALENT_CANDIDATE';cost='LOW';value='HIGH';predicted_pass_gain=17;recommendation='One fixed closed Shulker geometry and transform, parameterized by 17 entity textures.'},
        [pscustomobject]@{priority='A';group_id='TRIDENT_GUI';audit_bucket='SPECIAL_RENDERER';client_type='display_context -> minecraft:model';items_affected=1;strategy='STATIC_EQUIVALENT_CANDIDATE';cost='LOW';value='HIGH';predicted_pass_gain=1;recommendation='Resolve the GUI select branch and bake the existing item/trident generated model; do not implement the held special renderer.'},
        [pscustomobject]@{priority='A';group_id='POTION_TINTS';audit_bucket='DYNAMIC_ITEM';client_type='minecraft:model + minecraft:potion tint';items_affected=4;strategy='LIGHTWEIGHT_SPECIAL_RENDERER';cost='LOW';value='HIGH';predicted_pass_gain=4;recommendation='One tint compositor for potion, splash potion, lingering potion and tipped arrow.'},
        [pscustomobject]@{priority='B';group_id='BED_FAMILY';audit_bucket='SPECIAL_RENDERER';client_type='minecraft:bed composite';items_affected=16;strategy='STATIC_EQUIVALENT_CANDIDATE';cost='MEDIUM';value='HIGH';predicted_pass_gain=16;recommendation='One two-part Bed generator with 16 vanilla entity textures; Faithful 26.2 supplies no bed entity overrides.'},
        [pscustomobject]@{priority='B';group_id='BANNERS';audit_bucket='DYNAMIC_ITEM';client_type='minecraft:banner';items_affected=16;strategy='LIGHTWEIGHT_SPECIAL_RENDERER';cost='MEDIUM';value='HIGH';predicted_pass_gain=16;recommendation='BannerVisualDescriptor plus ordered BannerPatternLayers compositor; avoid static base-color-only icons.'},
        [pscustomobject]@{priority='C';group_id='FIXED_MOB_HEADS';audit_bucket='DYNAMIC_ITEM';client_type='minecraft:head';items_affected=6;strategy='STATIC_EQUIVALENT_CANDIDATE';cost='LOW';value='MEDIUM';predicted_pass_gain=6;recommendation='One fixed Skull geometry/transform with six built-in entity textures; player_head is excluded.'},
        [pscustomobject]@{priority='C';group_id='COPPER_GOLEM_STATUES';audit_bucket='SPECIAL_RENDERER';client_type='minecraft:copper_golem_statue + block_state';items_affected=8;strategy='LIGHTWEIGHT_SPECIAL_RENDERER';cost='MEDIUM';value='LOW';predicted_pass_gain=8;recommendation='One pose-aware renderer covers four poses and eight oxidation/wax item IDs.'},
        [pscustomobject]@{priority='C';group_id='CONDUIT_STATIC';audit_bucket='SPECIAL_RENDERER';client_type='minecraft:conduit';items_affected=1;strategy='STATIC_EQUIVALENT_CANDIDATE';cost='MEDIUM';value='MEDIUM';predicted_pass_gain=1;recommendation='Generate only the fixed item shell; do not reproduce the active block entity animation.'},
        [pscustomobject]@{priority='C';group_id='DECORATED_POT';audit_bucket='SPECIAL_RENDERER';client_type='minecraft:decorated_pot';items_affected=1;strategy='LIGHTWEIGHT_SPECIAL_RENDERER';cost='MEDIUM';value='MEDIUM';predicted_pass_gain=1;recommendation='Persist PotDecorations and compose four pattern sides with a cache key.'},
        [pscustomobject]@{priority='C';group_id='LEATHER_HORSE_ARMOR';audit_bucket='SPECIAL_RENDERER';client_type='minecraft:model + minecraft:dye tint';items_affected=1;strategy='LIGHTWEIGHT_SPECIAL_RENDERER';cost='LOW';value='LOW';predicted_pass_gain=1;recommendation='Treat as ordinary two-layer tinted item, not a client special renderer.'},
        [pscustomobject]@{priority='C';group_id='FIREWORK_STAR';audit_bucket='DYNAMIC_ITEM';client_type='minecraft:model + minecraft:firework tint';items_affected=1;strategy='LIGHTWEIGHT_SPECIAL_RENDERER';cost='LOW';value='MEDIUM';predicted_pass_gain=1;recommendation='Two-layer constant/firework color compositor.'},
        [pscustomobject]@{priority='C';group_id='FILLED_MAP_TINT';audit_bucket='DYNAMIC_ITEM';client_type='minecraft:model + minecraft:map_color tint';items_affected=1;strategy='LIGHTWEIGHT_SPECIAL_RENDERER';cost='LOW';value='MEDIUM';predicted_pass_gain=1;recommendation='Inventory icon uses map_color tint, not live map pixels.'},
        [pscustomobject]@{priority='DEFERRED';group_id='AIR_NON_ITEM';audit_bucket='SPECIAL_RENDERER';client_type='minecraft:model item/air';items_affected=1;strategy='DEFERRED_DYNAMIC';cost='LOW';value='LOW';predicted_pass_gain=0;recommendation='Exclude from visible coverage; air is an empty slot.'},
        [pscustomobject]@{priority='DEFERRED';group_id='PLAYER_HEAD';audit_bucket='DYNAMIC_ITEM';client_type='minecraft:player_head';items_affected=1;strategy='DEFERRED_DYNAMIC';cost='HIGH';value='MEDIUM';predicted_pass_gain=0;recommendation='Depends on profile and skin texture resolution; keep separate from fixed mob heads.'},
        [pscustomobject]@{priority='DEFERRED';group_id='CLOCK_COMPASS';audit_bucket='DYNAMIC_ITEM';client_type='range_dispatch time/compass';items_affected=3;strategy='DEFERRED_DYNAMIC';cost='HIGH';value='HIGH';predicted_pass_gain=0;recommendation='World time, dimension and target-angle dependent; a fixed frame would be false coverage.'}
    )
    if (($priority | Measure-Object items_affected -Sum).Sum -ne 88) {
        throw "Priority groups must explain all 88 deferred audit rows"
    }
    $priority | Export-Csv -LiteralPath $priorityOutput -Delimiter "`t" -NoTypeInformation -Encoding UTF8

    Write-Host "SPECIAL_RENDERER analysis: rows=$($rows.Count) static=$(@($rows | Where-Object suggested_strategy -eq 'STATIC_EQUIVALENT_CANDIDATE').Count) lightweight=$(@($rows | Where-Object suggested_strategy -eq 'LIGHTWEIGHT_SPECIAL_RENDERER').Count) deferred=$(@($rows | Where-Object suggested_strategy -eq 'DEFERRED_DYNAMIC').Count)"
    Write-Host "Priority matrix: groups=$($priority.Count) items=$((($priority | Measure-Object items_affected -Sum).Sum)) priorityA=$((($priority | Where-Object priority -eq 'A' | Measure-Object items_affected -Sum).Sum))"
} finally {
    $zip.Dispose()
}
