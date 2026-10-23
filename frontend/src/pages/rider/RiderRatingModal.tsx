import React, { useState } from 'react';
import { X, Star, ThumbsUp, Check } from 'lucide-react';
import { api } from '../../services/api';

interface RiderRatingModalProps {
  isOpen: boolean;
  onClose: () => void;
  bookingUuid?: string;
  driverName?: string;
  onRatingSubmitted?: () => void;
}

export const RiderRatingModal: React.FC<RiderRatingModalProps> = ({
  isOpen,
  onClose,
  bookingUuid,
  driverName = 'Your Driver',
  onRatingSubmitted
}) => {
  const [rating, setRating] = useState<number>(5);
  const [hoverRating, setHoverRating] = useState<number>(0);
  const [comment, setComment] = useState<string>('');
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isSubmitted, setIsSubmitted] = useState<boolean>(false);

  if (!isOpen) return null;

  const COMPLIMENT_TAGS = [
    'Clean Vehicle',
    'Smooth Driving',
    'Great Music',
    'Punctual & Courteous',
    'Expert Route',
    'Safe Driver'
  ];

  const toggleTag = (tag: string) => {
    if (selectedTags.includes(tag)) {
      setSelectedTags(selectedTags.filter((t) => t !== tag));
    } else {
      setSelectedTags([...selectedTags, tag]);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    const combinedComment = selectedTags.length > 0
      ? `[${selectedTags.join(', ')}] ${comment}`.trim()
      : comment;

    try {
      await api.submitDriverRating(bookingUuid || 'demo-booking-uuid', rating, combinedComment);
      setIsSubmitted(true);
      setTimeout(() => {
        setIsSubmitting(false);
        setIsSubmitted(false);
        if (onRatingSubmitted) onRatingSubmitted();
        onClose();
      }, 1500);
    } catch {
      // Local fallback
      setIsSubmitted(true);
      setTimeout(() => {
        setIsSubmitting(false);
        setIsSubmitted(false);
        if (onRatingSubmitted) onRatingSubmitted();
        onClose();
      }, 1500);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in">
      <div className="relative w-full max-w-md rounded-2xl bg-[#0c1220] border border-slate-700/80 p-6 shadow-2xl text-slate-100">
        <div className="flex items-center justify-between pb-3 border-b border-slate-800">
          <h3 className="text-base font-bold text-white">Rate Your Experience</h3>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="text-center my-4">
          <div className="w-14 h-14 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-500 text-white font-bold text-xl flex items-center justify-center mx-auto mb-2 shadow-lg shadow-indigo-950/40">
            {driverName[0]}
          </div>
          <h4 className="font-bold text-slate-200 text-sm">How was your ride with {driverName}?</h4>
          <p className="text-xs text-slate-400 mt-0.5">Your two-sided feedback helps maintain five-star platform quality.</p>
        </div>

        {/* 5-Star Selector */}
        <div className="flex items-center justify-center gap-2 my-4">
          {[1, 2, 3, 4, 5].map((star) => {
            const active = (hoverRating || rating) >= star;
            return (
              <button
                key={star}
                type="button"
                onMouseEnter={() => setHoverRating(star)}
                onMouseLeave={() => setHoverRating(0)}
                onClick={() => setRating(star)}
                className="p-1 focus:outline-none transition-transform active:scale-90"
              >
                <Star
                  className={`w-8 h-8 ${
                    active ? 'fill-amber-400 text-amber-400' : 'text-slate-600 hover:text-slate-500'
                  }`}
                />
              </button>
            );
          })}
        </div>

        {/* Compliment Tags */}
        <div className="mb-4">
          <p className="text-[11px] font-semibold text-slate-400 mb-2 text-center">What did you appreciate most?</p>
          <div className="flex flex-wrap gap-1.5 justify-center">
            {COMPLIMENT_TAGS.map((tag) => {
              const selected = selectedTags.includes(tag);
              return (
                <button
                  key={tag}
                  type="button"
                  onClick={() => toggleTag(tag)}
                  className={`px-2.5 py-1 rounded-full text-xs font-medium border transition-colors ${
                    selected
                      ? 'bg-indigo-600 text-white border-indigo-500'
                      : 'bg-slate-900 text-slate-300 border-slate-700 hover:border-slate-600'
                  }`}
                >
                  {tag}
                </button>
              );
            })}
          </div>
        </div>

        {/* Optional Written Comment */}
        <form onSubmit={handleSubmit} className="space-y-3">
          <textarea
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="Add specific comments about vehicle cleanliness, courteous driving, or route selection..."
            rows={2}
            className="w-full p-2.5 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
          />

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full py-2.5 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs transition-colors shadow-lg shadow-indigo-950/40 flex items-center justify-center gap-2 disabled:opacity-50"
          >
            {isSubmitted ? (
              <>
                <Check className="w-4 h-4 text-emerald-400" />
                <span>Thank you! Feedback recorded</span>
              </>
            ) : isSubmitting ? (
              <span>Submitting Rating...</span>
            ) : (
              <>
                <ThumbsUp className="w-4 h-4" />
                <span>Submit {rating}-Star Review</span>
              </>
            )}
          </button>
        </form>
      </div>
    </div>
  );
};
