// プレイヤーがサーバーにログインしたときに実行されるイベント
PlayerEvents.loggedIn(event => {
  // 'starting_items'という目印（ステージ）をまだ持っていないか確認
  if (!event.player.stages.has('starting_items')) {
    
    // --- ここに渡したいアイテムを記述 ---
    event.player.give('ftbquests:book');
    
    // アイテムを渡した後、二度とこの処理が実行されないように目印を付ける
    event.player.stages.add('starting_items');
  }
})