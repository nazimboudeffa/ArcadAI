import java.util.ArrayList;
import java.util.List;

import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener {

    private Mirror mirror = new Mirror();

    private Game game;

    private Player self;
	  private int cyclesForSearching = 0;
	  private int maxCyclesForSearching = 0;

	  Unit bunkerBuilder;

    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        System.out.println("New unit discovered " + unit.getType());
    }

    @Override
    public void onStart() {
        game = mirror.getGame();
        self = game.self();
		    bunkerBuilder = null;

        //Use BWTA to analyze map
        //This may take a few minutes if the map is processed first time!
        System.out.println("Analyzing map...");
        BWTA.readMap();
        BWTA.analyze();
        System.out.println("Map data ready");

        int i = 0;
        for(BaseLocation baseLocation : BWTA.getBaseLocations()){
        	System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
        	for(Position position : baseLocation.getRegion().getPolygon().getPoints()){
        		System.out.print(position + ", ");
        	}
        	System.out.println();
        }

    }

    @Override
    public void onFrame() {
    //game.setTextSize(10);
    game.drawTextScreen(10, 10, "Playing as " + self.getName() + " - " + self.getRace());

    StringBuilder units = new StringBuilder("My units:\n");
	  List<Unit> workers = new ArrayList<>();
	  List<Unit> barracks = new ArrayList<>();
	  List<Unit> marines = new ArrayList<>();
	  Unit commandCenter = null;
    Unit bunker = null;

		// iterate through my units
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.getType().isWorker()) {
				workers.add(myUnit);
			}
			if (myUnit.getType() == UnitType.Terran_Command_Center) {
				commandCenter = myUnit;
			}
			if (myUnit.getType() == UnitType.Terran_Barracks) {
				barracks.add(myUnit);
			}
			if (myUnit.getType() == UnitType.Terran_Marine) {
				marines.add(myUnit);
			}
			if (myUnit.isUnderAttack() && myUnit.canAttack()) {
				myUnit.attack(myUnit.getPosition());
			}
		}

		if (workers.size() >= 8) {
			bunkerBuilder = workers.get(8);
			TilePosition buildTile = getBuildTile(bunkerBuilder, UnitType.Terran_Barracks, bunkerBuilder.getTilePosition());
			System.out.println("Going to build barrack");
			if (buildTile == null) {
				bunkerBuilder.build(UnitType.Terran_Bunker, buildTile);
				System.out.println("Building barrack");
			} else {
				bunkerBuilder.move(BWTA.getNearestChokepoint(bunkerBuilder.getPosition()).getCenter());
				System.out.println("Where do i build barrack");
			}
		}

        //iterate through my workers
        for (Unit myUnit : workers) {
            units.append(myUnit.getType()).append(" ").append(myUnit.getTilePosition()).append("\n");

            //if it's a worker and it's idle, send it to the closest mineral patch
            if (myUnit.getType().isWorker() && myUnit.isIdle()) {
                Unit closestMineral = null;

                //find the closest mineral
                for (Unit neutralUnit : game.neutral().getUnits()) {
                    if (neutralUnit.getType().isMineralField()) {
                        if (closestMineral == null || myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
                            closestMineral = neutralUnit;
                        }
                    }
                }

                //if a mineral patch was found, send the worker to gather it
                if (closestMineral != null) {
                    myUnit.gather(closestMineral, false);
                }
            }
        }

		if (commandCenter.getTrainingQueue().isEmpty()) {
			commandCenter.build(UnitType.AllUnits.Terran_SCV);
		}

		for (Unit barrack : barracks) {
			if (barrack.getTrainingQueue().isEmpty()) {
				barrack.build(UnitType.AllUnits.Terran_Marine);
			}
		}


        //draw my units on screen
        game.drawTextScreen(10, 25, units.toString());
    }

 // Returns a suitable TilePosition to build a given building type near
 	// specified TilePosition aroundTile, or null if not found. (builder
 	// parameter is our worker)
 	public TilePosition getBuildTile(Unit builder, UnitType buildingType, TilePosition aroundTile) {
 		TilePosition ret = null;
 		int maxDist = 3;
 		int stopDist = 40;

 		// Refinery, Assimilator, Extractor
 		if (buildingType.isRefinery()) {
 			for (Unit n : game.neutral().getUnits()) {
 				cyclesForSearching++;
 				if ((n.getType() == UnitType.Resource_Vespene_Geyser)
 						&& (Math.abs(n.getTilePosition().getX() - aroundTile.getX()) < stopDist)
 						&& (Math.abs(n.getTilePosition().getY() - aroundTile.getY()) < stopDist))
 					return n.getTilePosition();
 			}
 		}

 		while ((maxDist < stopDist) && (ret == null)) {
 			for (int i = aroundTile.getX() - maxDist; i <= aroundTile.getX() + maxDist; i++) {
 				for (int j = aroundTile.getY() - maxDist; j <= aroundTile.getY() + maxDist; j++) {
 					if (game.canBuildHere(new TilePosition(i, j), buildingType, builder, false)) {
 						// units that are blocking the tile
 						boolean unitsInWay = false;
 						for (Unit u : game.getAllUnits()) {
 							cyclesForSearching++;
 							if (u.getID() == builder.getID())
 								continue;
 							if ((Math.abs(u.getTilePosition().getX() - i) < 4)
 									&& (Math.abs(u.getTilePosition().getY() - j) < 4))
 								unitsInWay = true;
 						}
 						if (!unitsInWay) {
 							cyclesForSearching++;
 							return new TilePosition(i, j);
 						}
 						// creep for Zerg
 						if (buildingType.requiresCreep()) {
 							boolean creepMissing = false;
 							for (int k = i; k <= i + buildingType.tileWidth(); k++) {
 								for (int l = j; l <= j + buildingType.tileHeight(); l++) {
 									cyclesForSearching++;
 									if (!game.hasCreep(k, l))
 										creepMissing = true;
 									break;
 								}
 							}
 							if (creepMissing)
 								continue;
 						}
 					}
 				}
 			}
 			maxDist += 2;
 		}

 		if (ret == null)
 			game.printf("Unable to find suitable build position for " + buildingType.toString());
 		return ret;
 	}

    public static void main(String[] args) {
        new TestBot1().run();
    }
}
