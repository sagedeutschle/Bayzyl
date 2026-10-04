import Foundation
@main struct FixtureMain {
 static func object<T:Encodable>(_ value:T) throws -> [String:Any] { try JSONSerialization.jsonObject(with:JSONEncoder().encode(value)) as! [String:Any] }
 static func solitaire(_ game:SolitaireGame) throws -> [String:Any] { var value=try object(game);value["foundations"]=try Dictionary(uniqueKeysWithValues:Suit.allCases.map { ($0.rawValue,try JSONSerialization.jsonObject(with:JSONEncoder().encode(game.foundations[$0] ?? []))) });return value }
 static func crazy(_ game:CrazyEightGame) throws -> [String:Any] {var value=try object(game);value["hands"]=try Dictionary(uniqueKeysWithValues:[CrazyEightPlayer.host,.guest].map { ($0.rawValue,try JSONSerialization.jsonObject(with:JSONEncoder().encode(game.hand(for:$0)))) });value["winner"]=game.winner?.rawValue ?? NSNull() as Any;return value}
 static func main() throws {
  var output:[[String:Any]]=[]
  for seed:UInt64 in [42,0,UInt64.max] {
   var sol=SolitaireGame.newGame(seed:seed,drawCount:3);let solStart=try solitaire(sol);var solActions:[[String:Any]]=[]
   for _ in 0..<9 {_=sol.drawFromStock();solActions.append(["type":"draw"])}
   _=sol.autoCollectToFoundations();solActions.append(["type":"auto"])
   output.append(["game":"solitaire","seed":String(seed),"options":["drawCount":3],"initial":solStart,"actions":solActions,"final":try solitaire(sol)])
   var spi=SpiderGame.newGame(seed:seed);let spiStart=try object(spi);var spiActions:[[String:Any]]=[["type":"deal"]];_=spi.dealRow()
   for _ in 0..<3 {var chosen=false;for from in 0..<10 {for index in spi.tableau[from].indices {for to in 0..<10 {if chosen {continue};var next=spi;if next.moveRun(from:from,cardIndex:index,to:to){spi=next;chosen=true;spiActions.append(["type":"move","from":from,"index":index,"to":to])}}}}}
   output.append(["game":"spider","seed":String(seed),"initial":spiStart,"actions":spiActions,"final":try object(spi)])
   var c=CrazyEightGame.newGame(seed:seed);let cStart=try crazy(c);var cActions:[[String:Any]]=[]
   for _ in 0..<14 {if c.isGameOver{break};let hand=c.hand(for:c.currentPlayer);if let index=hand.firstIndex(where:{c.canPlay($0)}){cActions.append(["type":"play","index":index,"suit":"hearts"]);_=c.playCard(hand[index],declaredSuit:.hearts)}else{cActions.append(["type":"draw"]);_=c.drawCard()}}
   output.append(["game":"crazy-8","seed":String(seed),"options":["mode":"passAndPlay"],"initial":cStart,"actions":cActions,"final":try crazy(c)])
   var solo=CrazyEightGame.newGame(seed:seed);let soloStart=try crazy(solo);var soloActions:[[String:Any]]=[]
   for _ in 0..<8 {
    if solo.isGameOver {break}
    let hand=solo.hand(for:.host)
    if let index=hand.firstIndex(where:{solo.canPlay($0)}) {soloActions.append(["type":"play","index":index,"suit":"spades"]);_=solo.playCard(hand[index],declaredSuit:.spades)} else {soloActions.append(["type":"draw"]);_=solo.drawCard()}
    if let choice=CrazyEightAI().move(in:solo) {switch choice {case .draw: _=solo.drawCard();case .play(let card,let suit): _=solo.playCard(card,declaredSuit:suit)}}
   }
   output.append(["game":"crazy-8","seed":String(seed),"options":["mode":"soloBot"],"initial":soloStart,"actions":soloActions,"final":try crazy(solo)])
  }
  FileHandle.standardOutput.write(try JSONSerialization.data(withJSONObject:output,options:[.sortedKeys,.prettyPrinted]))
 }
}
