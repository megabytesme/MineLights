impl super::Game<'_> {
    pub fn minelights_end_flash(&self) -> f32 {
        self.end.flash.intensity(1.0)
    }
}
