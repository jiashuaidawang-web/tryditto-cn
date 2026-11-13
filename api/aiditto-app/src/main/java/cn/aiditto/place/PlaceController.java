package cn.aiditto.place;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceController {
    private final PlaceService places;

    public PlaceController(PlaceService places) {
        this.places = places;
    }

    @GetMapping("/search")
    List<PlaceService.PlaceView> search(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "zh") String lang) {
        return places.search(q, lang);
    }
}
