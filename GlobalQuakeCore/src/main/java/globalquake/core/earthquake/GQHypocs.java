package globalquake.core.earthquake;

import globalquake.core.earthquake.data.Cluster;
import globalquake.core.earthquake.data.PickedEvent;
import globalquake.core.earthquake.data.PreliminaryHypocenter;
import globalquake.core.geo.taup.TauPTravelTimeCalculator;
import globalquake.jni.GQNativeFunctions;
import globalquake.utils.GeoUtils;

import java.util.List;

public class GQHypocs {

    private static boolean cudaLoaded = false;
    private static final float RADIANS = (float) (Math.PI / 180.0);

    private static final int MAX_POINTS = 6000_000;
    private static final float[] depth_profiles = new float[]{ 50.0f, 10.0f, 5.0f, 2.0f, 0.5f};
    private static final int[] point_profiles = new int[] { 6000_000, 300_000, 100_000, 40_000, 10_000};
    private static final float[] dist_profiles = new float[]{ 100.0f, 20.0f, 4.0f, 0.8f, 0.2f};

    static {
        try {
            System.loadLibrary("gq_hypocs");
            initCuda();
        } catch(UnsatisfiedLinkError e){
            System.err.println("Failed to load CUDA: "+e.getMessage());
        }

        for(int i = 0; i < depth_profiles.length; i++){
            System.err.printf("Iteration #%d difficulty: %.2fK%n", i, 750.0 / depth_profiles[i] * point_profiles[i] / 1000.0);
        }

        for(int i = 0; i < depth_profiles.length; i++) {
            double distKM = dist_profiles[i] / 360.0* GeoUtils.EARTH_CIRCUMFERENCE;
            System.err.printf("Iteration #%d space H %.2fkm V %fkm%n", i, Math.sqrt((distKM * distKM) / point_profiles[i]), depth_profiles[i]);
        }
    }

    private static void initCuda() {
        boolean init = true;

        init &= GQNativeFunctions.copyPTravelTable(TauPTravelTimeCalculator.getTravelTable().p_travel_table, (float) TauPTravelTimeCalculator.MAX_DEPTH);
        init &= GQNativeFunctions.initCUDA(MAX_POINTS, depth_profiles);

        if(init) {
            System.err.println("CUDA Loaded successfully");
            cudaLoaded = true;
        } else {
            System.err.println("CUDA Failed to load!");
        }
    }

    public synchronized static PreliminaryHypocenter findHypocenter(List<PickedEvent> pickedEventList, Cluster cluster, int from) {
        float[] stations_array = new float[pickedEventList.size() * 4];

        long time = pickedEventList.get(0).pWave();

        for (int i = 0; i < pickedEventList.size(); i++) {
            PickedEvent pickedEvent = pickedEventList.get(i);
            stations_array[i] = (float) pickedEvent.lat() * RADIANS;
            stations_array[i + pickedEventList.size()] = (float) pickedEvent.lon() * RADIANS;
            stations_array[i + 2 * pickedEventList.size()] = (float) pickedEvent.elevation();
            stations_array[i + 3 * pickedEventList.size()] = (float) ((pickedEvent.pWave() - time) / 1000.0);
        }

        float[] result = {
                (float) ((cluster.getPreviousHypocenter() != null ? cluster.getPreviousHypocenter().lat : cluster.getRootLat())  * RADIANS),
                (float) ((cluster.getPreviousHypocenter() != null ? cluster.getPreviousHypocenter().lon : cluster.getRootLon()) * RADIANS)
        };


        for(int i = from; i < depth_profiles.length; i++){
            result = GQNativeFunctions.findHypocenter(stations_array, result[0], result[1], point_profiles[i], i, dist_profiles[i] * RADIANS);

            if (result == null) {
                return null;
            }

        }

        return new PreliminaryHypocenter(result[0] / RADIANS, result[1] / RADIANS, result[2], (long) (result[3] * 1000.0 + time),0,0);
    }

    public static boolean isCudaLoaded() {
        return cudaLoaded;
    }
}
