// --- Minecraft goma鯖 ---

import crafttweaker.api.recipe.FurnaceRecipeManager;

//================================================
// 完成品アイテムID
//================================================

// zz複葉機
val biplane = <item:immersive_aircraft:biplane>;
// zzTempad
val tempad = <item:tempad:tempad>;
// zzサムライ製鋼インゴット
val samurai = <item:samurai_dynasty:steel_ingot>;

//================================================
// 素材アイテムID
//================================================

// zz複葉機
val ni = <item:minecraft:netherite_ingot>;
val cover = <item:immersive_aircraft:hull_reinforcement>;
val engine =  <item:immersive_aircraft:nether_engine>;
val propeller = <item:immersive_aircraft:enhanced_propeller>;

// zzTempad
val qb = <item:minecraft:quartz_block>;
val pearl = <item:minecraft:ender_pearl>;
val glass = <item:minecraft:glass>;
val rlamp = <item:minecraft:redstone_lamp>;

//================================================
// 既存レシピ削除
//================================================
craftingTable.remove(biplane);
craftingTable.remove(tempad);

//================================================
// 定型クラフトレシピ
//================================================
// --- 複葉機 ---
craftingTable.addShaped("biplane", biplane, [
    [ni, cover, ni],
    [cover, engine, propeller],
    [ni, cover, ni]
]);
// --- Tempad ---
craftingTable.addShaped("tempad", tempad, [
    [qb, qb, qb],
    [rlamp, glass, pearl],
    [qb, qb, qb]
]);


//================================================
// かまどレシピ
//================================================
<recipetype:minecraft:blasting>.remove(<item:samurai_dynasty:steel_ingot>);


