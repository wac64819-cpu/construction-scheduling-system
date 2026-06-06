package construction.constraint.temporal;

import construction.constraint.PredType;

public final class TemporalMath {

    private TemporalMath() {}

    public static double computeDelta(
            double di,
            double dj,
            double lag,
            PredType type) {

        switch (type) {
            case FS:
                // S_succ >= End_pred + lag
                // S_succ >= (S_pred + di) + lag
                return di + lag;

            case SS:
                // S_succ >= S_pred + lag
                return lag;

            case FF:
                // End_succ >= End_pred + lag
                // S_succ + dj >= S_pred + di + lag
                // S_succ >= S_pred + di - dj + lag
                return di - dj + lag;

            case SF:
                // End_succ >= S_pred + lag
                // S_succ + dj >= S_pred + lag
                // S_succ >= S_pred + lag - dj
                return lag - dj;
            default:
                throw new IllegalArgumentException();
        }
    }
}