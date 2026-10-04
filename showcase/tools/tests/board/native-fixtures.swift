// Compile alongside the unmodified native Foundation model files. Writes JSON to stdout.
import Foundation
@main struct FixtureMain {
 static func main() throws {
  var result:[String:Any]=[:]
  var c=ConnectFourGame();for x in [3,2,3,2,4,2,5,2] {_ = c.dropToken(in:x)}
  result["connect-four"]=["actions":[3,2,3,2,4,2,5,2],"board":c.board.map{$0?.rawValue.lowercased() as Any? ?? NSNull()},"currentPlayer":c.currentPlayer.rawValue.lowercased(),"winner":c.winner?.rawValue.lowercased() as Any? ?? NSNull()]
  var r=ReversiGame();var ri:[Int]=[];for _ in 0..<30 {guard let m=r.legalMoves().first else {r.passIfNeeded();continue};ri.append(m.row*8+m.col);_ = r.applyMove(row:m.row,col:m.col)}
  result["reversi"]=["actions":ri,"board":r.board.map{$0?.rawValue.lowercased() as Any? ?? NSNull()},"currentPlayer":r.currentPlayer.rawValue.lowercased()]
  var g=GomokuGame();let gi=[112,0,113,15,114,30,115,45,116];for i in gi {_ = g.placeStone(row:i/15,col:i%15)}
  result["gomoku"]=["actions":gi,"board":g.board.map{$0?.rawValue.lowercased() as Any? ?? NSNull()},"currentPlayer":g.currentPlayer.rawValue.lowercased(),"winner":g.winner?.rawValue.lowercased() as Any? ?? NSNull()]
  var ch=CheckersGame();var ca:[[String:Int]]=[];for _ in 0..<28 {guard let m=ch.legalMoves().first else {break};ca.append(["from":m.from.row*8+m.from.col,"to":m.to.row*8+m.to.col]);_ = ch.applyMove(m)}
  result["checkers"]=["actions":ca,"board":ch.board.map {p -> Any in if let p {return ["player":p.player.rawValue.lowercased(),"kind":p.kind.rawValue]};return NSNull()},"currentPlayer":ch.currentPlayer.rawValue.lowercased(),"activeJumpOrigin":ch.activeJumpOrigin.map{$0.row*8+$0.col} as Any? ?? NSNull()]
  let sea=SeaBattleGame.newGame(seed:42)
  result["sea-battle"]=["host":sea.board(for:.host).shipCells.map{$0.row*10+$0.col}.sorted(),"guest":sea.board(for:.guest).shipCells.map{$0.row*10+$0.col}.sorted()]
  var p=Position.initial;let moves=[[12,28],[52,36],[6,21],[57,42],[5,26],[62,45],[4,6]]
  for a in moves {guard let m=MoveGenerator.legalMoves(in:p).first(where:{$0.from.index==a[0] && $0.to.index==a[1]}) else {fatalError("fixture illegal")};p=MoveGenerator.makeMove(m,in:p)}
  result["chess"]=["actions":moves,"board":p.board.map{p -> Any in guard let p else{return NSNull()};return p.color == .white ? p.type.letter : p.type.letter.lowercased()},"currentPlayer":p.sideToMove.rawValue,"legalCount":MoveGenerator.legalMoves(in:p).count,"halfmoveClock":p.halfmoveClock]
  let ai=CheckersAI(player:.dark,difficulty:.normal);if let m=ai.move(in:CheckersGame()){result["checkersBot"]=["from":m.from.row*8+m.from.col,"to":m.to.row*8+m.to.col]}
  var cb=ConnectFourGame();for x in [3,2,4] {_ = cb.dropToken(in:x)}
  result["connectBot"]=["actions":[3,2,4],"column":ConnectFourAI(player:cb.currentPlayer,targetELO:1200).move(in:cb)!]
  let rm=ReversiAI(player:r.currentPlayer,targetELO:1200).move(in:r)!
  result["reversiBot"]=["actions":ri,"index":rm.row*8+rm.col]
  var gb=GomokuGame();let gis=[112,113,97,127,111,114];for i in gis {_ = gb.placeStone(row:i/15,col:i%15)}
  let gm=GomokuAI(player:gb.currentPlayer,targetELO:1200).move(in:gb)!
  result["gomokuBot"]=["actions":gis,"index":gm.row*15+gm.col]
  let sm=SeaBattleAI(difficulty:.normal,seed:42).shot(for:.host,in:sea)!
  result["seaBot"]=["index":sm.row*10+sm.col]
  let data=try JSONSerialization.data(withJSONObject:result,options:[.sortedKeys,.prettyPrinted]);print(String(decoding:data,as:UTF8.self))
 }
}
