/*
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.pokegoapi.api.map;

import POGOProtos.Map.Fort.FortDataOuterClass.FortData;
import POGOProtos.Map.Fort.FortTypeOuterClass.FortType;
import POGOProtos.Map.MapCellOuterClass;
import POGOProtos.Map.Pokemon.MapPokemonOuterClass.MapPokemon;
import POGOProtos.Map.Pokemon.NearbyPokemonOuterClass;
import POGOProtos.Map.Pokemon.WildPokemonOuterClass;
import POGOProtos.Map.SpawnPointOuterClass;
import POGOProtos.Networking.Requests.Messages.CatchPokemonMessageOuterClass.CatchPokemonMessage;
import POGOProtos.Networking.Requests.Messages.EncounterMessageOuterClass;
import POGOProtos.Networking.Requests.Messages.FortDetailsMessageOuterClass.FortDetailsMessage;
import POGOProtos.Networking.Requests.Messages.FortSearchMessageOuterClass.FortSearchMessage;
import POGOProtos.Networking.Requests.Messages.GetMapObjectsMessageOuterClass.GetMapObjectsMessage;
import POGOProtos.Networking.Requests.RequestTypeOuterClass;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass.CatchPokemonResponse;
import POGOProtos.Networking.Responses.EncounterResponseOuterClass.EncounterResponse;
import POGOProtos.Networking.Responses.FortDetailsResponseOuterClass;
import POGOProtos.Networking.Responses.FortSearchResponseOuterClass.FortSearchResponse;
import POGOProtos.Networking.Responses.GetMapObjectsResponseOuterClass;
import com.google.protobuf.InvalidProtocolBufferException;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.map.fort.FortDetails;
import com.pokegoapi.api.map.pokemon.CatchablePokemon;
import com.pokegoapi.api.map.pokemon.NearbyPokemon;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.google.common.geometry.MutableInteger;
import com.pokegoapi.google.common.geometry.S2CellId;
import com.pokegoapi.google.common.geometry.S2LatLng;
import com.pokegoapi.main.ServerRequest;
import java8.util.function.Function;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;


public class Map {
	private static long NEW_MAP_OBJECTS_EXPIRY = 60000; // 60 seconds
	private PokemonGo api;
	private long lastMapUpdate;
	private MapObjects lastMapObjects;
	private double lastLong;
	private double lastLat;
	@Getter
	@Setter
	private boolean useCache;
	@Getter
	@Setter
	private boolean trackUpdate;

	/**
	 * Instantiates a new Map.
	 *
	 * @param api the api
	 */
	public Map(PokemonGo api) {
		this.api = api;
		lastMapUpdate = 0;
		useCache = true;
		trackUpdate = true;
	}

	/**
	 * Gets a new map objects if there has been a lat/long change or the last request was done greater then
	 * NEW_MAP_OBJECTS_EXPIRY.
	 *
	 * @return a List of CatchablePokemon at your current location
	 */
	private MapObjects getRetainedMapObject() throws LoginFailedException, RemoteServerException {
		// get new MapObjects or used existing one
		if (!useCache) {
			return lastMapObjects;
		}
		if (api.getLatitude() != lastLat && api.getLongitude() != lastLong
				|| (System.currentTimeMillis() - lastMapUpdate) > NEW_MAP_OBJECTS_EXPIRY) {
			getMapObjects(); // should update the lastMapObjects variable
		}

		return lastMapObjects;
	}


	/**
	 * Returns a list of catchable pokemon around the current location.
	 *
	 * @return a List of CatchablePokemon at your current location
	 */
	public List<CatchablePokemon> getCatchablePokemon() throws LoginFailedException, RemoteServerException {
		List<CatchablePokemon> catchablePokemons = new ArrayList<>();
		MapObjects objects = getRetainedMapObject();

		for (MapPokemon mapPokemon : objects.getCatchablePokemons()) {
			catchablePokemons.add(new CatchablePokemon(api, mapPokemon));
		}
		for (WildPokemonOuterClass.WildPokemon wildPokemon : objects.getWildPokemons()) {
			catchablePokemons.add(new CatchablePokemon(api, wildPokemon));
		}

		return catchablePokemons;
	}


	/**
	 * Returns a list of nearby pokemon (non-catchable).
	 *
	 * @return a List of NearbyPokemon at your current location
	 */
	public List<NearbyPokemon> getNearbyPokemon() throws LoginFailedException, RemoteServerException {
		List<NearbyPokemon> pokemons = new ArrayList<>();
		MapObjects objects = getRetainedMapObject();

		for (NearbyPokemonOuterClass.NearbyPokemon pokemon : objects.getNearbyPokemons()) {
			pokemons.add(new NearbyPokemon(pokemon));
		}

		return pokemons;
	}

	/**
	 * Returns a list of spawn points.
	 *
	 * @return list of spawn points
	 */
	public List<Point> getSpawnPoints() throws LoginFailedException, RemoteServerException {
		List<Point> points = new ArrayList<>();
		MapObjects objects = getRetainedMapObject();

		for (SpawnPointOuterClass.SpawnPoint point : objects.getSpawnPoints()) {
			points.add(new Point(point));
		}

		return points;
	}

	/**
	 * Returns a list of decimated spawn points at current location.
	 *
	 * @return list of spawn points
	 */
	public List<Point> getDecimatedSpawnPoints() throws LoginFailedException, RemoteServerException {
		List<Point> points = new ArrayList<>();
		MapObjects objects = getRetainedMapObject();

		for (SpawnPointOuterClass.SpawnPoint point : objects.getDecimatedSpawnPoints()) {
			points.add(new Point(point));
		}

		return points;
	}

	/**
	 * Returns MapObjects around your current location.
	 *
	 * @return MapObjects at your current location
	 */
	public MapObjects getMapObjects() throws LoginFailedException, RemoteServerException {
		return getMapObjects(9);
	}

	/**
	 * Returns MapObjects around your current location within a given width.
	 *
	 * @param width width
	 * @return MapObjects at your current location
	 */
	public MapObjects getMapObjects(int width) throws LoginFailedException, RemoteServerException {
		return getMapObjects(
				getCellIds(
						api.getLatitude(),
						api.getLongitude(),
						width),
				api.getLatitude(),
				api.getLongitude(),
				api.getAltitude());
	}

	/**
	 * Returns 9x9 cells with the requested lattitude/longitude in the center cell.
	 *
	 * @param latitude  latitude
	 * @param longitude longitude
	 * @return MapObjects in the given cells
	 */
	@Deprecated
	public MapObjects getMapObjects(double latitude, double longitude)
			throws LoginFailedException, RemoteServerException {
		return getMapObjects(latitude, longitude, 9);
	}

	/**
	 * Returns the cells requested, you should send a latitude/longitude to fake a near location.
	 *
	 * @param cellIds   List of cellIds
	 * @param latitude  latitude
	 * @param longitude longitude
	 * @return MapObjects in the given cells
	 */
	@Deprecated
	public MapObjects getMapObjects(List<Long> cellIds, double latitude, double longitude)
			throws LoginFailedException, RemoteServerException {
		return getMapObjects(cellIds, latitude, longitude, 0);
	}

	/**
	 * Returns `width` * `width` cells with the requested latitude/longitude in the center.
	 *
	 * @param latitude  latitude
	 * @param longitude longitude
	 * @param width     width
	 * @return MapObjects in the given cells
	 */
	@Deprecated
	public MapObjects getMapObjects(double latitude, double longitude, int width)
			throws LoginFailedException, RemoteServerException {
		return getMapObjects(getCellIds(latitude, longitude, width), latitude, longitude);
	}

	/**
	 * Returns the cells requested.
	 *
	 * @param cellIds   cellIds
	 * @param latitude  latitude
	 * @param longitude longitude
	 * @return MapObjects in the given cells
	 */
	@Deprecated
	public MapObjects getMapObjects(List<Long> cellIds, double latitude, double longitude, double altitude)
			throws LoginFailedException, RemoteServerException {
		api.setLatitude(latitude);
		api.setLongitude(longitude);
		api.setAltitude(altitude);
		return getMapObjects(cellIds);
	}

	/**
	 * Returns the cells requested.
	 *
	 * @param cellIds List of cellId
	 * @return MapObjects in the given cells
	 */
	public MapObjects getMapObjects(List<Long> cellIds) throws LoginFailedException, RemoteServerException {
		GetMapObjectsMessage.Builder builder = GetMapObjectsMessage.newBuilder()
				.setLatitude(api.getLatitude())
				.setLongitude(api.getLongitude());

		int index = 0;
		for (Long cellId : cellIds) {
			builder.addCellId(cellId);
			long time = 0;
			if (trackUpdate) {
				time = lastMapUpdate;
			}
			builder.addSinceTimestampMs(lastMapUpdate);
			index++;
		}

		ServerRequest serverRequest = new ServerRequest(RequestTypeOuterClass.RequestType.GET_MAP_OBJECTS, builder.build());
		api.getRequestHandler().request(serverRequest);
		api.getRequestHandler().sendServerRequests();
		GetMapObjectsResponseOuterClass.GetMapObjectsResponse response = null;
		try {
			response = GetMapObjectsResponseOuterClass.GetMapObjectsResponse.parseFrom(serverRequest.getData());
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException(e);
		}

		MapObjects result = new MapObjects(api);
		for (MapCellOuterClass.MapCell mapCell : response.getMapCellsList()) {
			result.addNearbyPokemons(mapCell.getNearbyPokemonsList());
			result.addCatchablePokemons(mapCell.getCatchablePokemonsList());
			result.addWildPokemons(mapCell.getWildPokemonsList());
			result.addDecimatedSpawnPoints(mapCell.getDecimatedSpawnPointsList());
			result.addSpawnPoints(mapCell.getSpawnPointsList());

			java.util.Map<FortType, List<FortData>> groupedForts = StreamSupport.stream(mapCell.getFortsList())
					.collect(Collectors.groupingBy(new Function<FortData, FortType>() {
						@Override
						public FortType apply(FortData fortData) {
							return fortData.getType();
						}
					}));
			result.addGyms(groupedForts.get(FortType.GYM));
			result.addPokestops(groupedForts.get(FortType.CHECKPOINT));
		}

		lastMapObjects = result;
		lastMapUpdate = System.currentTimeMillis();
		return result;
	}

	/**
	 * Get a list of all the Cell Ids.
	 *
	 * @param latitude  latitude
	 * @param longitude longitude
	 * @param width     width
	 * @return List of Cells
	 */
	public List<Long> getCellIds(double latitude, double longitude, int width) {
		S2LatLng latLng = S2LatLng.fromDegrees(latitude, longitude);
		S2CellId cellId = S2CellId.fromLatLng(latLng).parent(15);

		lastLat = api.getLatitude();
		lastLong = api.getLongitude();

		MutableInteger index = new MutableInteger(0);
		MutableInteger jindex = new MutableInteger(0);

		int level = cellId.level();
		int size = 1 << (S2CellId.MAX_LEVEL - level);
		int face = cellId.toFaceIJOrientation(index, jindex, null);

		List<Long> cells = new ArrayList<Long>();

		int halfWidth = (int) Math.floor(width / 2);
		for (int x = -halfWidth; x <= halfWidth; x++) {
			for (int y = -halfWidth; y <= halfWidth; y++) {
				cells.add(S2CellId.fromFaceIJ(face, index.intValue() + x * size, jindex.intValue() + y * size).parent(15).id());
			}
		}
		return cells;
	}

	/**
	 * Gets fort details.
	 *
	 * @param id  the id
	 * @param lon the lon
	 * @param lat the lat
	 * @return the fort details
	 * @throws LoginFailedException  the login failed exception
	 * @throws RemoteServerException the remote server exception
	 */
	public FortDetails getFortDetails(String id, long lon, long lat) throws LoginFailedException, RemoteServerException {
		FortDetailsMessage reqMsg = FortDetailsMessage.newBuilder()
				.setFortId(id)
				.setLatitude(lat)
				.setLongitude(lon)
				.build();

		ServerRequest serverRequest = new ServerRequest(RequestTypeOuterClass.RequestType.FORT_DETAILS, reqMsg);
		api.getRequestHandler().request(serverRequest);
		api.getRequestHandler().sendServerRequests();
		FortDetailsResponseOuterClass.FortDetailsResponse response = null;
		try {
			response = FortDetailsResponseOuterClass.FortDetailsResponse.parseFrom(serverRequest.getData());
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException(e);
		}
		return new FortDetails(response);
	}

	/**
	 * Search fort fort search response.
	 *
	 * @param fortData the fort data
	 * @return the fort search response
	 * @throws LoginFailedException  the login failed exception
	 * @throws RemoteServerException the remote server exception
	 */
	@Deprecated
	public FortSearchResponse searchFort(FortData fortData) throws LoginFailedException, RemoteServerException {
		FortSearchMessage reqMsg = FortSearchMessage.newBuilder()
				.setFortId(fortData.getId())
				.setFortLatitude(fortData.getLatitude())
				.setFortLongitude(fortData.getLongitude())
				.setPlayerLatitude(api.getLatitude())
				.setPlayerLongitude(api.getLongitude())
				.build();
		ServerRequest serverRequest = new ServerRequest(RequestTypeOuterClass.RequestType.FORT_SEARCH, reqMsg);
		api.getRequestHandler().request(serverRequest);
		api.getRequestHandler().sendServerRequests();
		FortSearchResponse response = null;
		try {
			response = FortSearchResponse.parseFrom(serverRequest.getData());
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException(e);
		}
		return response;
	}

	/**
	 * Encounter pokemon encounter response.
	 *
	 * @param catchablePokemon the catchable pokemon
	 * @return the encounter response
	 * @throws LoginFailedException  the login failed exception
	 * @throws RemoteServerException the remote server exception
	 */
	@Deprecated
	public EncounterResponse encounterPokemon(MapPokemon catchablePokemon)
			throws LoginFailedException, RemoteServerException {

		EncounterMessageOuterClass.EncounterMessage reqMsg = EncounterMessageOuterClass.EncounterMessage.newBuilder()
				.setEncounterId(catchablePokemon.getEncounterId())
				.setPlayerLatitude(api.getLatitude())
				.setPlayerLongitude(api.getLongitude())
				.setSpawnpointId(catchablePokemon.getSpawnpointId())
				.build();
		ServerRequest serverRequest = new ServerRequest(RequestTypeOuterClass.RequestType.ENCOUNTER, reqMsg);
		api.getRequestHandler().request(serverRequest);
		api.getRequestHandler().sendServerRequests();
		EncounterResponse response = null;
		try {
			response = EncounterResponse.parseFrom(serverRequest.getData());
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException(e);
		}
		return response;
	}

	/**
	 * Catch pokemon catch pokemon response.
	 *
	 * @param catchablePokemon      the catchable pokemon
	 * @param normalizedHitPosition the normalized hit position
	 * @param normalizedReticleSize the normalized reticle size
	 * @param spinModifier          the spin modifier
	 * @param pokeball              the pokeball
	 * @return the catch pokemon response
	 * @throws LoginFailedException  the login failed exception
	 * @throws RemoteServerException the remote server exception
	 */
	@Deprecated
	public CatchPokemonResponse catchPokemon(
			MapPokemon catchablePokemon,
			double normalizedHitPosition,
			double normalizedReticleSize,
			double spinModifier,
			int pokeball)
			throws LoginFailedException, RemoteServerException {

		CatchPokemonMessage reqMsg = CatchPokemonMessage.newBuilder()
				.setEncounterId(catchablePokemon.getEncounterId())
				.setHitPokemon(true)
				.setNormalizedHitPosition(normalizedHitPosition)
				.setNormalizedReticleSize(normalizedReticleSize)
				.setSpawnPointGuid(catchablePokemon.getSpawnpointId())
				.setSpinModifier(spinModifier)
				.setPokeball(pokeball)
				.build();
		ServerRequest serverRequest = new ServerRequest(RequestTypeOuterClass.RequestType.CATCH_POKEMON, reqMsg);
		api.getRequestHandler().request(serverRequest);
		api.getRequestHandler().sendServerRequests();
		CatchPokemonResponse response = null;
		try {
			response = CatchPokemonResponse.parseFrom(serverRequest.getData());
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException(e);
		}
		return response;
	}
}