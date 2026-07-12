package com.gadang.algorithm;

import java.util.List;

/**
 * Course-specific place lookup contract.
 *
 * Implementations may prepare the map/place cache first, but the returned
 * candidates must come from the stored place data rather than live scoring APIs.
 */
public interface CourseCandidateProvider {

    List<PlaceCandidate> getCourseCandidates(double lat, double lng,
                                             int radiusMeters, List<String> categories,
                                             String regionHint);
}
