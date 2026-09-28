package pt.ulisboa.tecnico.cnv.loadbalancer;

public enum WorkloadType {
    GAME_OF_LIFE,
    FIFTEEN_PUZZLE,
    CAPTURE_THE_FLAG,
    UNKNOWN;


    public static String toString(WorkloadType type){
        switch (type){
            case GAME_OF_LIFE:
                return "GoL";
            case FIFTEEN_PUZZLE:
                return "15P";
            case CAPTURE_THE_FLAG:
                return "CTF";
            default:
                return "Unknown";
        }
    }
}

