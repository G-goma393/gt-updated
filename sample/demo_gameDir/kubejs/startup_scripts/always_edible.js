// ゲームのアイテムプロパティが変更可能になるタイミングで実行されるイベント
ItemEvents.modification(event => {
  
  // バニラの「#minecraft:foods」というアイテムタグに含まれる全てのアイテムを取得
  Ingredient.of('#minecraft:foods').getItemIds().forEach(itemId => {
    
    // エラーが発生してもスクリプトが停止しないように、try...catchで処理を囲む
    try {
      // 取得した各アイテムのプロパティを変更する
      event.modify(itemId, itemProps => {
        // 「常時食べられる」設定をtrue（有効）にする
        itemProps.alwaysEdible = true
      })
    } catch (error) {
      // もしエラーが発生しても、ここでは何もしないで次のアイテムに進む
      // これにより、BlockItemのような特殊なアイテムを安全にスキップできる
    }
  })
})