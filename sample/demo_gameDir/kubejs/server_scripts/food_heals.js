// プレイヤーが食べ物を食べ終えたときに実行されるイベント
ItemEvents.foodEaten(event => {

  // 食べたアイテムの食事に関するプロパティを取得
  const food = event.item.getFoodProperties(event.player);

  // もし、それが食べ物でなければ（プロパティがなければ）処理を終了
  if (!food) {
    return;
  }

  // --- 設定 ---
  // 体力の回復量を、満腹度回復量の何倍にするか設定します。
  // 1.0 = 満腹度1ポイントにつき、体力1（ハート半分）回復
  // 0.5 = 満腹度1ポイントにつき、体力0.5（ハート4分の1）回復
  const healingMultiplier = 2.0;
  // --- 設定はここまで ---

  // 食べ物の満腹度回復量（肉ゲージの回復量）を取得
  const hungerRestored = food.getNutrition();

  // 最終的な回復量を計算
  const healAmount = hungerRestored * healingMultiplier;
  
  // イベントからプレイヤー情報を取得
  const player = event.player;

  // 計算された回復量が0より大きい場合のみ、プレイヤーを回復させる
  if (healAmount > 0) {
    player.heal(healAmount);
  }
})