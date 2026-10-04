import Foundation
@main struct WorkshopFixture {
 static func main() throws {
  let id=UUID(uuidString:"00000000-0000-0000-0000-000000000001")!
  var document=LegoBuildDocument()
  document.add(LegoBrick(id:id,size:.twoByFour,color:.classicRed,origin:LegoGridPoint(x:10,y:8),layer:0))
  document.move(id:id,dx:3,dy:-10,dLayer:20,gridSize:12)
  document.rotate(id:id,by:1,gridSize:12)
  let data=try JSONEncoder().encode(document)
  var value=try JSONSerialization.jsonObject(with:data) as! [String:Any]
  var bricks=value["bricks"] as! [[String:Any]];bricks[0]["id"]=1;value["bricks"]=bricks
  value["xml"]=BrickLinkWantedListExporter.xml(for:document)
  FileHandle.standardOutput.write(try JSONSerialization.data(withJSONObject:value,options:[.sortedKeys,.prettyPrinted]))
 }
}
