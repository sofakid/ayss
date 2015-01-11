SpaceNav {

    const event_names = #[\on_shift_lr, \on_shift_ud,
                          \on_tilt_lr,  \on_tilt_ud,
                          \on_lift,     \on_turn];

    // 3dconnexion SpaceNavigator
    const vendorId  = 1133;
    const productId = 50726;

    var hid;
    var <handlers;

    *new {
        ^super.new.init;
    }

    init {

        handlers = ();
        event_names.do {
            | x |
            handlers[x] = { | v | }; // 0.0 <= v <= 1.0
        };

        HID.findAvailable;
        hid = HID.open(vendorId, productId);

        // i do the lookup each time so we can swap out the handlers whenever
        hid.elements[0].action = { | v | handlers[\on_shift_lr].(v) };
        hid.elements[1].action = { | v | handlers[\on_shift_ud].(v) };
        hid.elements[2].action = { | v | handlers[\on_lift    ].(v) };
        hid.elements[3].action = { | v | handlers[\on_tilt_ud ].(v) };
        hid.elements[4].action = { | v | handlers[\on_tilt_lr ].(v) };
        hid.elements[5].action = { | v | handlers[\on_turn    ].(v) };

    }

    put {
        | key, val |
        handlers[key] = val;
    }

}

SpaceNavThreshy {

    const home           = 0.5;
    const high_zone      = 0.8;
    const low_zone       = 0.2;
    const home_low_zone  = 0.4;
    const home_high_zone = 0.6;

    const event_names = #[\on_shift_left, \on_shift_right, \on_shift_lr_home,
                          \on_shift_up,   \on_shift_down,  \on_shift_ud_home,
                          \on_tilt_left,  \on_tilt_right,  \on_tilt_lr_home,
                          \on_tilt_up,    \on_tilt_down,   \on_tilt_ud_home,
                          \on_lift_up,    \on_lift_down,   \on_lift_home,
                          \on_turn_left,  \on_turn_right,  \on_turn_home];

    var  spaceNav;
    var <handlers;

    *new {
        ^super.new.init;
    }

    init {

        var f;

        spaceNav = SpaceNav();
        handlers = ();

        event_names.do {
            | x |
            handlers[x] = { | d | };
        };

        f = {
            | v, on_high, on_home, on_low |

            // we're sending the distance from home (which is 0.5)
            case {v > high_zone} {
                handlers[on_high].(v - home); // won't be negative

            } {v < low_zone} {
                handlers[on_low].(home - v); // won't be negative

            } {(v > home_low_zone) and: (v < home_high_zone)} {
                handlers[on_home].(home - v); // can be negative
            };

        };

        spaceNav[\on_shift_lr] = { | v | f.(v, \on_shift_right, \on_shift_lr_home, \on_shift_left ) };
        spaceNav[\on_shift_ud] = { | v | f.(v, \on_shift_up,    \on_shift_ud_home, \on_shift_down ) };
        spaceNav[\on_tilt_lr]  = { | v | f.(v, \on_tilt_left,   \on_tilt_lr_home,  \on_tilt_right ) };
        spaceNav[\on_tilt_ud]  = { | v | f.(v, \on_tilt_up,     \on_tilt_ud_home,  \on_tilt_down  ) };
        spaceNav[\on_lift]     = { | v | f.(v, \on_lift_down,   \on_lift_home,     \on_lift_up    ) };
        spaceNav[\on_turn]     = { | v | f.(v, \on_turn_right,  \on_turn_home,     \on_turn_left  ) };
    }

    put {
        | key, val |
        handlers[key] = val;
    }

}


Knobby {

    const event_names = #[\on_shift_left, \on_shift_right, \on_shift_up,  \on_shift_down,
                          \on_tilt_left,  \on_tilt_right,  \on_tilt_up,   \on_tilt_down,
                          \on_lift_up,    \on_lift_down,   \on_turn_left, \on_turn_right];

    var spaceNav;
    var entering;

    var <first;
    var <update;
    var <done;

    *new {
        ^super.new.init();
    }

    init {
        var f, f_done;

        spaceNav = SpaceNavThreshy.new;

        first = ();
        update = ();
        done = ();

        entering = ();

        event_names.do {
            | x |
            first[x]  = { | d | };
            update[x] = { | d | };
            done[x]   = {};

            entering[x] = true;
        };

        // first and update handlers
        event_names.do {
            | x |
            spaceNav[x] = {
                | d |
                if (entering[x] == true) {
                    entering[x] = false;
                    first[x].(d);
                } {
                    update[x].(d);
                };
            };
        };

        // done handlers
        f_done = {
            | x |

            if (entering[x] == false) {
                done[x].();
                entering[x] = true;
            };
        };

        spaceNav[\on_shift_lr_home] = {
            f_done.(\on_shift_left);
            f_done.(\on_shift_right);
        };

        spaceNav[\on_shift_ud_home] = {
            f_done.(\on_shift_up);
            f_done.(\on_shift_down);
        };

        spaceNav[\on_tilt_lr_home] = {
            f_done.(\on_tilt_left);
            f_done.(\on_tilt_right);
        };

        spaceNav[\on_tilt_ud_home] = {
            f_done.(\on_tilt_up);
            f_done.(\on_tilt_down);
        };

        spaceNav[\on_lift_home] = {
            f_done.(\on_lift_up);
            f_done.(\on_lift_down);
        };

        spaceNav[\on_turn_home] = {
            f_done.(\on_turn_left);
            f_done.(\on_turn_right);
        };

    }

}

KnobbyPanel {

    var knobs;
    var i, n;

    var knobby;
    var current_knob;
    var sched_right;
    var sched_left;

    var speed;

    *new {
        | list_of_knobs |
        ^super.new.init(list_of_knobs);
    }

    init {
        | list_of_knobs |

        var first, update, done;

        knobs = list_of_knobs;
        i = 0;
        n = knobs.size;
        current_knob = knobs[0];

        speed = 1;

        knobby = Knobby();

        first = knobby.first;
        update = knobby.update;
        done = knobby.done;

        first[\on_shift_right] = { this.selectNextKnob; };
        first[\on_shift_left]  = { this.selectPrevKnob; };

        first[\on_lift_up]   = { this.colorKnob(Color.red); };
        first[\on_lift_down] = { this.colorKnob(Color.green); };

        first[\on_turn_right] = {

            sched_right = 0.1;
            AppClock.sched(0.0, {
                current_knob.increment(speed);
                sched_right
            });
        };

        update[\on_turn_right] = {
            | d |
            this.accel(d);
        };

        done[\on_turn_right] = {
            sched_right = nil
        };


        first[\on_turn_left] = {

            sched_left = 0.1;
            AppClock.sched(0.0, {
                current_knob.decrement(speed);
                sched_left
            });
        };

        update[\on_turn_left] = {
            | d |
            this.accel(d);
        };

        done[\on_turn_left] = {
            sched_left = nil
        };


    }

    accel {
        | d |
        var m = 4, b = -0.75;

        d = m * d + b;

        speed = 1 + d * 3;
    }

    nextKnob {
        i = 1 + i % n;
        ^knobs[i];
    }

    prevKnob {
        i = if (i == 0) {
            n - 1
        } {
            i - 1
        }
        ^knobs[i];
    }

    selectNextKnob {
        AppClock.sched(0.0, {
            current_knob.background = Color.gray;
            current_knob = this.nextKnob;
            current_knob.background = Color.green;
            nil;
        });
    }

    selectPrevKnob {
        AppClock.sched(0.0, {
            current_knob.background = Color.gray;
            current_knob = this.prevKnob;
            current_knob.background = Color.green;
            nil;
        });
    }

    colorKnob {
        | c |
        AppClock.sched(0.0, {
            current_knob.background = c;
            nil;
        });
    }
}
