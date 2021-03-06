/*
 * Copyright (c) 2020 Nikifor Fedorov
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *     SPDX-License-Identifier: Apache-2.0
 *     Contributors:
 *         Nikifor Fedorov and others
 */

package ru.krivocraft.tortoise.core.rating;

import ru.krivocraft.tortoise.core.data.TracksProvider;
import ru.krivocraft.tortoise.core.model.Track;


public class RatingImpl implements Rating {

    private final TracksProvider tracksStorageManager;

    public RatingImpl(TracksProvider tracksStorageManager) {
        this.tracksStorageManager = tracksStorageManager;
    }

    @Override
    public void rate(Track.Reference reference, int delta) {
        Track track = tracksStorageManager.getTrack(reference);
        track.setRating(track.getRating() + delta);
        tracksStorageManager.updateTrack(track);
    }

}
