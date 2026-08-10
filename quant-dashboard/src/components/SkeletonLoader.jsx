
import { theme } from '../theme';

export const SkeletonCard = ({ height = '120px', width = '100%', style = {} }) => {
  return (
    <div
      style={{
        height,
        width,
        backgroundColor: 'rgba(30, 41, 59, 0.6)',
        borderRadius: '10px',
        border: `1px solid ${theme.border}`,
        position: 'relative',
        overflow: 'hidden',
        boxShadow: '0 4px 12px rgba(0, 0, 0, 0.25)',
        backdropFilter: 'blur(8px)',
        ...style
      }}
      className="skeleton-shimmer"
    >
      <div
        style={{
          width: '50%',
          height: '14px',
          backgroundColor: 'rgba(51, 65, 85, 0.5)',
          borderRadius: '4px',
          margin: '16px 0 12px 16px'
        }}
      />
      <div
        style={{
          width: '70%',
          height: '28px',
          backgroundColor: 'rgba(51, 65, 85, 0.7)',
          borderRadius: '4px',
          margin: '0 0 12px 16px'
        }}
      />
      <div
        style={{
          width: '40%',
          height: '12px',
          backgroundColor: 'rgba(51, 65, 85, 0.4)',
          borderRadius: '4px',
          margin: '0 0 16px 16px'
        }}
      />
    </div>
  );
};

export const StatCardsSkeleton = () => {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '20px', marginBottom: '30px' }}>
      <SkeletonCard height="130px" />
      <SkeletonCard height="130px" />
      <SkeletonCard height="130px" />
      <SkeletonCard height="130px" />
    </div>
  );
};

export const SkeletonTable = ({ rows = 4 }) => {
  return (
    <div style={{ backgroundColor: theme.cardBg, borderRadius: '10px', border: `1px solid ${theme.border}`, padding: '16px' }}>
      <div style={{ display: 'flex', gap: '16px', marginBottom: '16px' }}>
        {[...Array(4)].map((_, i) => (
          <div key={i} style={{ flex: 1, height: '20px', backgroundColor: 'rgba(51, 65, 85, 0.6)', borderRadius: '4px' }} className="skeleton-shimmer" />
        ))}
      </div>
      {[...Array(rows)].map((_, r) => (
        <div key={r} style={{ display: 'flex', gap: '16px', marginBottom: '12px' }}>
          {[...Array(4)].map((_, c) => (
            <div key={c} style={{ flex: 1, height: '16px', backgroundColor: 'rgba(51, 65, 85, 0.3)', borderRadius: '4px' }} className="skeleton-shimmer" />
          ))}
        </div>
      ))}
    </div>
  );
};

export const SkeletonChart = ({ height = '300px' }) => {
  return (
    <div
      style={{
        height,
        width: '100%',
        backgroundColor: theme.cardBg,
        borderRadius: '10px',
        border: `1px solid ${theme.border}`,
        padding: '20px',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between'
      }}
      className="skeleton-shimmer"
    >
      <div style={{ width: '30%', height: '20px', backgroundColor: 'rgba(51, 65, 85, 0.6)', borderRadius: '4px' }} />
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: '12px', height: '70%' }}>
        {[...Array(12)].map((_, i) => (
          <div
            key={i}
            style={{
              flex: 1,
              height: `${20 + (i * 7) % 75}%`,
              backgroundColor: 'rgba(51, 65, 85, 0.4)',
              borderRadius: '4px 4px 0 0'
            }}
          />
        ))}
      </div>
    </div>
  );
};

