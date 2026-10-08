-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Migration: V2__seed_data.sql
-- Description: Seed/Reference Data for Testing & Demonstration (All 24 Tables)
-- Database Engine: InnoDB, Charset: utf8mb4
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. RIDE TYPES (INR Pricing Model)
-- -----------------------------------------------------------------------------
INSERT INTO ride_types (id, name, code, description, base_fare, per_km_rate, per_minute_rate, minimum_fare, cancellation_fee, capacity, icon_url, is_active)
VALUES 
(1, 'LEXOR Go', 'GO', 'Affordable everyday hatchback rides for up to 4 passengers', 50.00, 15.00, 2.50, 80.00, 30.00, 4, 'https://cdn.lexor.mobility/icons/go.png', TRUE),
(2, 'LEXOR XL', 'XL', 'Spacious SUV rides for groups up to 6 passengers', 90.00, 22.00, 3.50, 140.00, 50.00, 6, 'https://cdn.lexor.mobility/icons/xl.png', TRUE),
(3, 'LEXOR Premier', 'PREMIER', 'High-end luxury sedans with top-rated drivers', 120.00, 28.00, 4.50, 180.00, 70.00, 4, 'https://cdn.lexor.mobility/icons/premier.png', TRUE),
(4, 'LEXOR Green', 'GREEN', 'Eco-friendly electric and hybrid vehicle rides', 60.00, 16.00, 2.80, 90.00, 35.00, 4, 'https://cdn.lexor.mobility/icons/green.png', TRUE)
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- -----------------------------------------------------------------------------
-- 2. COUPONS
-- -----------------------------------------------------------------------------
INSERT INTO coupons (id, code, description, discount_type, discount_value, min_ride_amount, max_discount_amount, usage_limit, usage_count, valid_from, valid_until, is_active)
VALUES
(1, 'LEXOR50', '50% off your next ride up to ₹100', 'PERCENTAGE', 50.00, 150.00, 100.00, 1000, 5, '2026-01-01 00:00:00', '2026-12-31 23:59:59', TRUE),
(2, 'WELCOME100', '₹100 fixed discount for new users', 'FIXED_AMOUNT', 100.00, 250.00, 100.00, 5000, 12, '2026-01-01 00:00:00', '2026-12-31 23:59:59', TRUE)
ON DUPLICATE KEY UPDATE code=VALUES(code);

-- -----------------------------------------------------------------------------
-- 3. USERS (Admin, Rider, Driver)
-- -----------------------------------------------------------------------------
INSERT INTO users (id, uuid, first_name, last_name, email, phone_number, password_hash, role, status, email_verified, phone_verified)
VALUES
(1, '11111111-1111-4111-8111-111111111111', 'Rutuja', 'Admin', 'admin@lexor.mobility', '+919876543210', '$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T', 'ADMIN', 'ACTIVE', TRUE, TRUE),
(2, '22222222-2222-4222-8222-222222222222', 'Rajesh', 'Kumar', 'rajesh.kumar@example.com', '+919876543211', '$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T', 'RIDER', 'ACTIVE', TRUE, TRUE),
(3, '33333333-3333-4333-8333-333333333333', 'Vikram', 'Singh', 'vikram.singh@example.com', '+919876543212', '$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T', 'DRIVER', 'ACTIVE', TRUE, TRUE)
ON DUPLICATE KEY UPDATE email=VALUES(email);

-- -----------------------------------------------------------------------------
-- 4. ROLE PROFILES
-- -----------------------------------------------------------------------------
INSERT INTO admin_profiles (id, user_id, department, access_level)
VALUES (1, 1, 'Platform Operations', 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE department=VALUES(department);

INSERT INTO rider_profiles (id, user_id, rating, total_rides, preferred_payment_method, home_address, work_address)
VALUES (1, 2, 4.95, 12, 'UPI', 'MG Road, Indiranagar, Bengaluru, Karnataka 560038', 'Prestige Tech Park, Marathahalli, Bengaluru, Karnataka 560103')
ON DUPLICATE KEY UPDATE rating=VALUES(rating);

INSERT INTO driver_profiles (id, user_id, license_number, license_expiry_date, status, rating, total_trips, acceptance_rate, cancellation_rate, is_verified, background_check_status)
VALUES (1, 3, 'KA-01-20230009876', '2028-12-31', 'ONLINE', 4.90, 145, 98.50, 1.20, TRUE, 'PASSED')
ON DUPLICATE KEY UPDATE license_number=VALUES(license_number);

-- -----------------------------------------------------------------------------
-- 5. VEHICLE
-- -----------------------------------------------------------------------------
INSERT INTO vehicles (id, driver_id, make, model, year, color, license_plate, vin, ride_type_category, seat_capacity, is_active, is_inspected)
VALUES (1, 1, 'Toyota', 'Urban Cruiser Hyryder', 2023, 'Midnight Black', 'KA-01-EQ-4829', 'MA31234567890ABCD', 'SEDAN', 4, TRUE, TRUE)
ON DUPLICATE KEY UPDATE license_plate=VALUES(license_plate);

-- -----------------------------------------------------------------------------
-- 6. FAVORITE LOCATIONS & EMERGENCY CONTACTS
-- -----------------------------------------------------------------------------
INSERT INTO favorite_locations (id, rider_id, label, address, latitude, longitude)
VALUES 
(1, 1, 'Home', 'MG Road, Indiranagar, Bengaluru, Karnataka 560038', 12.97840000, 77.64080000),
(2, 1, 'Work', 'Prestige Tech Park, Marathahalli, Bengaluru, Karnataka 560103', 12.93720000, 77.69490000)
ON DUPLICATE KEY UPDATE label=VALUES(label);

INSERT INTO emergency_contacts (id, user_id, contact_name, contact_phone, relationship)
VALUES (1, 2, 'Priya Kumar', '+919876543299', 'Spouse')
ON DUPLICATE KEY UPDATE contact_name=VALUES(contact_name);

-- -----------------------------------------------------------------------------
-- 7. DEMO RIDE LIFECYCLE & BOOKINGS
-- -----------------------------------------------------------------------------

-- Ride Request (Status: MATCHED)
INSERT INTO ride_requests (id, uuid, rider_id, ride_type_id, pickup_address, pickup_latitude, pickup_longitude, drop_address, drop_latitude, drop_longitude, estimated_distance_km, estimated_duration_minutes, estimated_fare, coupon_id, status)
VALUES (1, '44444444-4444-4444-8444-444444444444', 1, 1, 'MG Road, Indiranagar, Bengaluru', 12.97840000, 77.64080000, 'Prestige Tech Park, Marathahalli, Bengaluru', 12.93720000, 77.69490000, 11.50, 32, 250.00, 1, 'MATCHED')
ON DUPLICATE KEY UPDATE uuid=VALUES(uuid);

-- Booking (Hashed OTP Security Model)
INSERT INTO bookings (id, uuid, ride_request_id, rider_id, driver_id, vehicle_id, ride_type_id, booking_status, otp_hash, otp_expires_at, otp_verified_at)
VALUES (1, '55555555-5555-4555-8555-555555555555', 1, 1, 1, 1, 1, 'CONFIRMED', '$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T', '2026-10-08 12:15:00', '2026-10-08 12:00:00')
ON DUPLICATE KEY UPDATE uuid=VALUES(uuid);

-- Completed Ride Record
INSERT INTO rides (id, uuid, booking_id, rider_id, driver_id, vehicle_id, ride_type_id, pickup_address, pickup_latitude, pickup_longitude, drop_address, drop_latitude, drop_longitude, status, actual_pickup_time, actual_drop_time, actual_distance_km, actual_duration_minutes, started_at, completed_at)
VALUES (1, '66666666-6666-4666-8666-666666666666', 1, 1, 1, 1, 1, 'MG Road, Indiranagar, Bengaluru', 12.97840000, 77.64080000, 'Prestige Tech Park, Marathahalli, Bengaluru', 12.93720000, 77.69490000, 'TRIP_COMPLETED', '2026-10-08 12:00:00', '2026-10-08 12:35:00', 11.80, 35, '2026-10-08 12:00:00', '2026-10-08 12:35:00')
ON DUPLICATE KEY UPDATE uuid=VALUES(uuid);

-- Status History Transitions
INSERT INTO ride_status_history (id, ride_id, status, changed_by_user_id, notes)
VALUES 
(1, 1, 'REQUESTED', 2, 'Rider submitted booking request'),
(2, 1, 'SEARCHING_DRIVER', 2, 'System searching nearby drivers'),
(3, 1, 'DRIVER_ASSIGNED', 3, 'Driver Vikram accepted booking'),
(4, 1, 'TRIP_STARTED', 3, 'OTP verified and trip started'),
(5, 1, 'TRIP_COMPLETED', 3, 'Arrived at destination')
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- Intermediate Ride Stop Waypoint
INSERT INTO ride_stops (id, ride_id, stop_order, stop_address, latitude, longitude, arrived_at, departed_at)
VALUES (1, 1, 1, 'Silk Board Junction, Outer Ring Rd, Bengaluru', 12.91720000, 77.62280000, '2026-10-08 12:15:00', '2026-10-08 12:18:00')
ON DUPLICATE KEY UPDATE stop_address=VALUES(stop_address);

-- Driver Telemetry Sample
INSERT INTO driver_locations (id, driver_id, ride_id, latitude, longitude, heading, speed_kmh)
VALUES (1, 1, 1, 12.95500000, 77.66500000, 145.00, 42.00)
ON DUPLICATE KEY UPDATE latitude=VALUES(latitude);

-- -----------------------------------------------------------------------------
-- 8. FINANCIAL, PAYMENTS & REFUNDS
-- -----------------------------------------------------------------------------
INSERT INTO fare_breakdowns (id, ride_id, base_fare, distance_fare, time_fare, surge_multiplier, surge_amount, discount_amount, cancellation_fee, tax_amount, total_fare, driver_earning, platform_commission, currency)
VALUES (1, 1, 50.00, 177.00, 87.50, 1.00, 0.00, 100.00, 0.00, 35.50, 250.00, 200.00, 50.00, 'INR')
ON DUPLICATE KEY UPDATE total_fare=VALUES(total_fare);

INSERT INTO payments (id, uuid, ride_id, rider_id, amount, payment_method, status)
VALUES (1, '77777777-7777-4777-8777-777777777777', 1, 1, 250.00, 'UPI', 'COMPLETED')
ON DUPLICATE KEY UPDATE uuid=VALUES(uuid);

INSERT INTO payment_transactions (id, transaction_reference, payment_id, gateway_provider, gateway_transaction_id, amount, currency, status)
VALUES (1, 'TXN-20261008-IN001', 1, 'RAZORPAY_SANDBOX', 'pay_M0000000000001', 250.00, 'INR', 'SUCCESS')
ON DUPLICATE KEY UPDATE transaction_reference=VALUES(transaction_reference);

-- Sample Refund Record (Adjustment/Promotional)
INSERT INTO refunds (id, uuid, payment_id, amount, reason, status, processed_at)
VALUES (1, '88888888-8888-4888-8888-888888888888', 1, 25.00, 'Traffic delay courtesy credit', 'PROCESSED', '2026-10-08 13:00:00')
ON DUPLICATE KEY UPDATE uuid=VALUES(uuid);

-- -----------------------------------------------------------------------------
-- 9. RATINGS, FEEDBACK & AI ANALYTICS
-- -----------------------------------------------------------------------------
INSERT INTO feedback (id, ride_id, rider_id, driver_id, rating, driver_rating, rider_rating, cleanliness_rating, safety_rating, comments)
VALUES (1, 1, 1, 1, 5, 5, 5, 5, 5, 'Extremely smooth ride and clean vehicle! Highly recommended driver.')
ON DUPLICATE KEY UPDATE rating=VALUES(rating);

INSERT INTO ai_feedback_analysis (id, feedback_id, sentiment, sentiment_score, topic, severity, summary, action_recommended)
VALUES (1, 1, 'POSITIVE', 0.9850, 'Overall Experience & Cleanliness', 'LOW', 'Customer praised driver professionalism and vehicle cleanliness.', 'Maintain driver reward eligibility.')
ON DUPLICATE KEY UPDATE sentiment=VALUES(sentiment);

-- -----------------------------------------------------------------------------
-- 10. NOTIFICATIONS, SAFETY & AUDIT LOGS
-- -----------------------------------------------------------------------------
INSERT INTO notifications (id, user_id, title, message, type, is_read)
VALUES (1, 2, 'Trip Completed', 'Thank you for riding with LEXOR! Your receipt for ₹250.00 is available.', 'RIDE_UPDATE', TRUE)
ON DUPLICATE KEY UPDATE title=VALUES(title);

-- Sample SOS Safety Event (Resolved Test)
INSERT INTO sos_events (id, ride_id, triggered_by_user_id, latitude, longitude, status, notes, triggered_at, resolved_at)
VALUES (1, 1, 2, 12.95500000, 77.66500000, 'RESOLVED', 'False alarm triggered during ride demo test. Rider confirmed safe.', '2026-10-08 12:10:00', '2026-10-08 12:12:00')
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- Sample Admin Audit Action
INSERT INTO admin_actions (id, admin_user_id, action_type, target_type, target_id, details, ip_address)
VALUES (1, 1, 'VERIFY_DRIVER_BACKGROUND', 'DRIVER', 1, '{"status": "PASSED", "verified_by": "Rutuja Admin", "license": "KA-01-20230009876"}', '192.168.1.50')
ON DUPLICATE KEY UPDATE action_type=VALUES(action_type);

SET FOREIGN_KEY_CHECKS = 1;
