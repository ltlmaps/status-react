(ns quo.components.bottom-sheet.view
  (:require [reagent.core :as reagent]
            [quo.animated :as animated]
            [quo.react-native :as rn]
            [quo.react :as react]
            [quo.platform :as platform]
            [quo.components.safe-area :as safe-area]
            [quo.components.bottom-sheet.style :as styles]
            [quo.gesture-handler :as gesture-handler]))

(def opacity-coeff 0.4)

(def spring-config {:damping                   15
                    :mass                      1
                    :stiffness                 150
                    :overshootClamping         false
                    :restSpeedThreshold        0.1
                    :restDisplacementThreshold 0.1})

(defn bottom-sheet-still-no-hooks []
  (let [content-height   (reagent/atom 0)
        external-visible (reagent/atom nil)
        visible          (reagent/atom nil)

        header-translation-y (animated/value 0)
        header-velocity-y    (animated/value (:undetermined gesture-handler/states))
        header-state         (animated/value (:undetermined gesture-handler/states))

        resistance (animated/divide header-translation-y 2)

        open       (animated/value 0)
        close      (animated/value 0)
        offset     (animated/value 0)
        clock      (animated/clock)
        body-ref   (react/create-ref)
        header-ref (react/create-ref)

        on-header-event (animated/event [{:nativeEvent
                                          {:translationY header-translation-y
                                           :state        header-state
                                           :velocityY    header-velocity-y}}]
                                        {:useNativeDriver true})
        on-body-event   (animated/event [{:nativeEvent
                                          {:translationY header-translation-y
                                           :state        header-state
                                           :velocityY    header-velocity-y}}])
        on-layout       (fn [evt]
                          (let [height (->> ^js evt
                                            .-nativeEvent
                                            .-layout
                                            .-height)]
                            (when (not= height @content-height)
                              (reset! content-height height))))
        close-sheet     (fn []
                          (animated/set-value close 1))
        memo-spring     (memoize
                         #(animated/with-spring
                            (merge {:value    resistance
                                    :velocity header-velocity-y
                                    :offset   offset
                                    :state    header-state
                                    :config   spring-config} %)))]
    ;; TODO(Ferossgp): Use hook to get safe-area and window size when available
    (fn [{:keys [content on-cancel disable-drag? show-handle? visible?
                backdrop-dismiss? safe-area window-height back-button-cancel]
         :or   {show-handle?       true
                backdrop-dismiss?  true
                back-button-cancel true
                window-height      800}}]
      (when-not (= @external-visible visible?)
        (reset! external-visible visible?)
        (if visible?
          (reset! visible true)
          (close-sheet)))
      (let [height           (+ @content-height
                                styles/border-radius)
            max-height       (- window-height
                                (:top safe-area)
                                styles/margin-top)
            sheet-height     (min max-height height)
            open-snap-point  (- sheet-height)
            close-snap-point 0
            on-close         (fn []
                               (reset! visible false)
                               (when on-cancel (on-cancel)))
            on-snap          (fn [pos]
                               (when (= (aget pos 0) close-snap-point)
                                 (on-close)))
            translate-y      (memo-spring {:snapPoints [open-snap-point close-snap-point]
                                           :onSnap     on-snap})
            opacity          (animated/interpolate translate-y
                                                   {:inputRange  [(* open-snap-point opacity-coeff) close-snap-point]
                                                    :outputRange [1 0]
                                                    :extrapolate (:clamp animated/extrapolate)})]
        [:<>
         ;; Animate open and close
         [animated/code
          {:key  (str open-snap-point on-cancel) ; TODO(Ferossgp): Replace with a hook
           :exec (animated/block
                  [(animated/cond*
                    open
                    [(animated/set offset
                                   (animated/re-timing {:from   offset
                                                        :to     open-snap-point
                                                        :clock  clock
                                                        :duration 250
                                                        ;; :config spring-config
                                                        }))
                     (animated/cond* (animated/not* (animated/clock-running clock))
                                     (animated/set open 0))])
                   (animated/cond*
                    close
                    [(animated/set offset
                                   (animated/re-timing {:from     offset
                                                        :to       close-snap-point
                                                        :clock    clock
                                                        :duration 150
                                                        :easing   (:ease-out animated/easings)}))
                     (animated/cond* (animated/not* (animated/clock-running clock))
                                     [(animated/set close 0)
                                      (animated/call* [] on-close)])])])}]
         [rn/modal {:visible                @visible
                    :transparent            true
                    :status-bar-translucent true
                    :presentation-style     :overFullScreen
                    :hardware-accelerated   true
                    :on-show                #(do
                                               (animated/set-value open 1))
                    :on-request-close       (fn []
                                              (when back-button-cancel
                                                (close-sheet)))}
          [rn/view {:style          styles/container
                    :pointer-events :box-none}
           [rn/touchable-without-feedback (merge {:style styles/container}
                                                 (when backdrop-dismiss?
                                                   {:on-press close-sheet}))
            [animated/view {:style (merge (styles/backdrop)
                                          {:opacity opacity})}]]
           [animated/view {:style (merge (styles/content-container window-height sheet-height)
                                         {:transform [{:translateY translate-y}
                                                      {:translateY (animated/add sheet-height window-height)}]})}
            [gesture-handler/pan-gesture-handler {:ref                  header-ref
                                                  :wait-for             body-ref
                                                  :enabled              (not disable-drag?)
                                                  :onGestureEvent       on-header-event
                                                  :onHandlerStateChange on-header-event}
             [animated/view  {:style styles/content-header}
              (when show-handle?
                [rn/view {:style styles/handle}])]]
            [gesture-handler/pan-gesture-handler {:ref                  body-ref
                                                  :wait-for             header-ref
                                                  :enabled              (and (not disable-drag?)
                                                                             (not= max-height sheet-height))
                                                  :onGestureEvent       on-body-event
                                                  :onHandlerStateChange on-body-event}
             ;; NOTE(Ferossgp): Use different drag event instead of scroll-view
             [animated/scroll-view {:bounces        false
                                    :style          {:flex 1}
                                    :scroll-enabled (= max-height sheet-height)}
              [animated/view {:style     {:padding-top    styles/vertical-padding
                                          :padding-bottom (+ styles/vertical-padding
                                                             (:bottom safe-area))}
                              :on-layout on-layout}
               [content]]]]]]]]))))

(defn bottom-sheet [props]
  [bottom-sheet-still-no-hooks (assoc props
                                      :window-height (rn/window-height)
                                      :safe-area {:top    44
                                                  :bottom 34})])
